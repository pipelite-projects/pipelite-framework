package io.pipelite.core.context.impl;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.components.CandidateComponentMetadata;
import io.pipelite.core.components.CandidateChannelAdapterResolver;
import io.pipelite.core.components.ChannelAdapterFactory;
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
    private final ChannelAdapterFactory channelAdapterFactory;
    private final CandidateChannelAdapterResolver channelAdapterResolver;
    private final Map<String, ChannelAdapter> channelAdapters;

    private final Collection<ChannelConfigurer<?>> channelConfigurers;

    public DefaultChannelAdapterManager(ExchangeFactory exchangeFactory) {
        Preconditions.notNull(exchangeFactory, "exchangeFactory is required and cannot be null");
        this.exchangeFactory = exchangeFactory;
        this.channelAdapterFactory = new ChannelAdapterFactory();
        this.channelAdapterResolver = new CandidateChannelAdapterResolver();
        this.channelAdapters = new ConcurrentHashMap<>();
        this.channelConfigurers = new ArrayList<>();
    }

    @Override
    public void scan() {

        // Find component candidates
        final Collection<CandidateComponentMetadata> candidateComponents = channelAdapterResolver.findCandidates();
        // create and register components
        candidateComponents.forEach(ccm -> {
            final ChannelAdapter channel = channelAdapterFactory.instantiateAdapter(ccm.getChannelAdapterType());
            registerChannelAdapter(ccm.getProtocolName(), channel);
        });
    }

    @Override
    public void registerChannelAdapter(String channelName, ChannelAdapter channelAdapter) {

        Preconditions.notNull(channelName, "componentName is required and cannot be null");
        Preconditions.notNull(channelAdapter, "component is required and cannot be null");
        if(channelAdapter instanceof ExchangeFactoryAware){
            ((ExchangeFactoryAware) channelAdapter).setExchangeFactory(exchangeFactory);
        }
        final ChannelAdapter oldChannelAdapter = channelAdapters.putIfAbsent(channelName, channelAdapter);
        if(oldChannelAdapter == null){
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
