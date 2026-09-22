/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pipelite.core.context.impl;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.components.ChannelAdapterDiscovery;
import io.pipelite.core.components.DuplicateChannelAdapterProtocolException;
import io.pipelite.core.context.ChannelAdapterManager;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultChannelAdapterManager implements ChannelAdapterManager {

    private final ExchangeFactory exchangeFactory;
    private final ChannelAdapterDiscovery channelAdapterDiscovery;
    private final Map<String, ChannelAdapter> channelAdapters;

    private final Collection<ChannelConfigurer<?>> channelConfigurers;

    public DefaultChannelAdapterManager(ExchangeFactory exchangeFactory) {
        Preconditions.notNull(exchangeFactory, "exchangeFactory is required and cannot be null");
        this.exchangeFactory = exchangeFactory;
        this.channelAdapterDiscovery = new ChannelAdapterDiscovery();
        this.channelAdapters = new ConcurrentHashMap<>();
        this.channelConfigurers = new ArrayList<>();
    }

    @Override
    public void scan() {
        channelAdapterDiscovery.discover().forEach(this::registerChannelAdapter);
    }

    @Override
    public void registerChannelAdapter(String channelName, ChannelAdapter channelAdapter) {

        Preconditions.notNull(channelName, "componentName is required and cannot be null");
        Preconditions.notNull(channelAdapter, "component is required and cannot be null");
        if(channelAdapter instanceof ExchangeFactoryAware){
            ((ExchangeFactoryAware) channelAdapter).setExchangeFactory(exchangeFactory);
        }
        // Fails fast (issue #57) instead of silently keeping the first registration and dropping
        // this one - putIfAbsent's own "someone already claimed this" case used to be treated as a
        // plain no-op, indistinguishable from an idempotent re-registration.
        final ChannelAdapter oldChannelAdapter = channelAdapters.putIfAbsent(channelName, channelAdapter);
        if(oldChannelAdapter != null){
            throw new DuplicateChannelAdapterProtocolException(channelName,
                oldChannelAdapter.getClass().getName(), channelAdapter.getClass().getName());
        }
        final Optional<ChannelConfigurer<?>> channelConfigurerHolder = channelConfigurers
            .stream()
            .filter(cc -> {
                final Class<? extends ChannelConfigurer<?>> configurerType = channelAdapter.getChannelConfigurerType();
                return configurerType != null && configurerType.isAssignableFrom(cc.getClass());
            })
            .findFirst();
        if(channelConfigurerHolder.isPresent()){
            final ChannelConfigurer<?> channelConfigurer = channelConfigurerHolder.get();
            channelAdapter.configure(channelConfigurer);
        }
    }

    @Override
    public void addChannelConfigurer(ChannelConfigurer<?> channelConfigurer) {
        channelConfigurers.add(channelConfigurer);
    }

    @Override
    public ChannelAdapter resolveChannel(String channelName) {
        if(channelAdapters.containsKey(channelName)){
            return channelAdapters.get(channelName);
        }
        throw new IllegalArgumentException(String.format("Unable to resolve component %s", channelName));
    }

    @Override
    public Optional<ChannelAdapter> tryResolveChannel(String channelName) {
        return Optional.ofNullable(channelAdapters.get(channelName));
    }

    @Override
    public void notifyFlowRegisterd(Flow flow) {
        channelAdapters.forEach((componentName, component) -> {
            if(component instanceof ContextEventListener){
                ((ContextEventListener)component).onFlowRegistered(flow);
            }
        });
    }

    @Override
    public void notifyContextStarted() {
        channelAdapters.forEach((componentName, component) -> {
            if(component instanceof ContextEventListener){
                ((ContextEventListener)component).onContextStarted();
            }
        });
    }

    @Override
    public void notifyContextStopped() {
        channelAdapters.forEach((componentName, component) -> {
            if(component instanceof ContextEventListener){
                ((ContextEventListener)component).onContextStopped();
            }
        });
    }
}
