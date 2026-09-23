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
import io.pipelite.core.config.EndpointURLPropertyResolver;
import io.pipelite.core.context.ChannelAdapterManager;
import io.pipelite.core.context.EndpointFactory;
import io.pipelite.core.context.UnsupportedSourceConcurrencyException;
import io.pipelite.core.context.internal.DestinationURLs;
import io.pipelite.core.context.internal.SourceURLs;
import io.pipelite.core.definition.TypedSourceDefinition;
import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.dsl.definition.SourceConfigurer;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.expression.support.ReflectionUtils;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.SourceConcurrencyConfigurer;
import io.pipelite.spi.flow.concurrent.SourceConcurrencyProperties;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.function.Consumer;

public class DefaultEndpointFactory implements EndpointFactory {

    private final ChannelAdapterManager channelAdapterManager;
    private EndpointURLPropertyResolver endpointURLPropertyResolver;

    public DefaultEndpointFactory(ChannelAdapterManager channelAdapterManager, EndpointURLPropertyResolver endpointURLPropertyResolver) {
        Preconditions.notNull(channelAdapterManager, "componentManager is required and cannot be null");
        Preconditions.notNull(endpointURLPropertyResolver, "endpointURLPropertyResolver is required and cannot be null");
        this.channelAdapterManager = channelAdapterManager;
        this.endpointURLPropertyResolver = endpointURLPropertyResolver;
    }

    public void setEndpointURLPropertyResolver(EndpointURLPropertyResolver endpointURLPropertyResolver) {
        Preconditions.notNull(endpointURLPropertyResolver, "endpointURLPropertyResolver is required and cannot be null");
        this.endpointURLPropertyResolver = endpointURLPropertyResolver;
    }

    /**
     * A URL with its {@code ${...}} placeholders resolved, the way {@link #createEndpoint} resolves it
     * before it builds the endpoint.
     */
    public String resolveURL(String rawURL) {
        return endpointURLPropertyResolver.resolve(rawURL);
    }

    @Override
    public Endpoint createEndpoint(EndpointDefinition endpointDefinition) {

        if(endpointDefinition instanceof TypedSourceDefinition typedSourceDefinition){
            return createTypedSourceEndpoint(typedSourceDefinition);
        }

        final String resolvedUrl = endpointURLPropertyResolver.resolve(endpointDefinition.getUrl());
        // Every endpoint is a URL (issues #111, #112): a value with no protocol, which the DSL could
        // not see because it came from a placeholder, is rejected here.
        if (endpointDefinition instanceof SourceDefinition) {
            SourceURLs.requireProtocol(resolvedUrl);
        } else {
            DestinationURLs.requireSinkProtocol(resolvedUrl);
        }
        final ChannelURL channelURL = ChannelURL.parse(resolvedUrl);
        final ChannelAdapter channel = channelAdapterManager.resolveChannel(channelURL.getProtocol());
        rejectSourceConcurrencyParams(channel, channelURL.getProtocol(), channelURL.getEndpointURL());
        final String endpointURL = applySourceConfigurer(endpointDefinition, channel, channelURL.getEndpointURL());
        return channel.createEndpoint(endpointURL);
    }

    /**
     * Lowers a {@code fromSource(url, configurer)} callback (see {@link SourceConfigurer}'s own
     * Javadoc) into the exact same query-string shape {@code EndpointURL} already supports, so
     * every downstream reader keeps working unchanged. {@code channel} is the adapter that builds
     * the endpoint and hands out its configurer: the queue one a {@link SourceConcurrencyConfigurer},
     * the others their own.
     * <p>
     * {@code channel.newSourceConfigurer()} is fetched unconditionally, even for the plain {@code
     * fromSource(String)} overload with no callback (issue #120) - an adapter's configurer can
     * pre-seed its own protocol-specific default (e.g. {@code TimeChannelAdapter} defaulting
     * {@code durableInbox} to {@code false}, since a {@code time://} tick is always regenerable and
     * never worth the durability #70 exists for) before any user callback runs, the same way {@link
     * #rejectSourceConcurrencyParams} already calls it unconditionally just above this method's own
     * call site. A no-op for every other adapter today: {@link SourceConfigurer#toQueryParameters()}
     * only ever emits a property that was actually set, so an adapter that pre-seeds nothing returns
     * an empty map here exactly as before. An adapter with no configurer at all (the {@code null}
     * default) still resolves the endpoint unchanged when no callback was given - the "does not
     * support a SourceConfigurer" failure is only for a caller that actually tried to use one.
     */
    private static String applySourceConfigurer(EndpointDefinition endpointDefinition, ChannelAdapter channel, String endpointURL) {

        if (!(endpointDefinition instanceof SourceDefinition sourceDefinition)) {
            return endpointURL;
        }
        final Consumer<SourceConfigurer> configurerCallback = sourceDefinition.getConfigurerCallback();
        final SourceConfigurer settings = channel.newSourceConfigurer();
        if (settings == null) {
            if (configurerCallback != null) {
                throw new IllegalStateException(String.format(
                    "Endpoint '%s' does not support a SourceConfigurer", endpointURL));
            }
            return endpointURL;
        }
        if (configurerCallback != null) {
            try {
                configurerCallback.accept(settings);
            } catch (ClassCastException exception) {
                throw new IllegalArgumentException(String.format(
                    "Wrong SourceConfigurer type supplied for endpoint '%s' - expected one accepting a %s",
                    endpointURL, settings.getClass().getSimpleName()), exception);
            }
        }

        return mergeQueryParameters(endpointURL, settings.toQueryParameters());
    }

    private static String mergeQueryParameters(String endpointURL, Map<String, String> extraParameters) {
        if (extraParameters.isEmpty()) {
            return endpointURL;
        }
        final StringBuilder mergedURL = new StringBuilder(endpointURL);
        mergedURL.append(endpointURL.indexOf('?') < 0 ? '?' : '&');
        boolean first = true;
        for (Map.Entry<String, String> parameter : extraParameters.entrySet()) {
            if (!first) {
                mergedURL.append('&');
            }
            mergedURL.append(parameter.getKey()).append('=').append(parameter.getValue());
            first = false;
        }
        return mergedURL.toString();
    }

    /**
     * {@code concurrency}/{@code executorType} only apply to a queue source, the one adapter whose
     * source configurer is a {@link SourceConcurrencyConfigurer} - see {@link
     * UnsupportedSourceConcurrencyException}. Deliberately does NOT call {@link
     * EndpointURL#parse(String)} (default resource pattern): some adapters (e.g. File) validate
     * their resource against a wider pattern of their own, and re-validating it here with the
     * default pattern would falsely reject otherwise-legitimate URLs. This only ever looks at the
     * raw query string, never the resource portion.
     */
    private static void rejectSourceConcurrencyParams(ChannelAdapter channel, String protocol, String endpointURL) {
        if (channel.newSourceConfigurer() instanceof SourceConcurrencyConfigurer) {
            return;
        }
        final int queryIndex = endpointURL.indexOf('?');
        if (queryIndex < 0) {
            return;
        }
        final String query = endpointURL.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            final String key = pair.split("=", 2)[0];
            if (SourceConcurrencyProperties.CONCURRENCY.equals(key) || SourceConcurrencyProperties.EXECUTOR_TYPE.equals(key)) {
                throw new UnsupportedSourceConcurrencyException(protocol, key);
            }
        }
    }

    private Endpoint createTypedSourceEndpoint(TypedSourceDefinition typedSourceDefinition){

        final Class<? extends Endpoint> endpointType = typedSourceDefinition.getEndpointType();
        try {
            final Constructor<? extends Endpoint> ctr = endpointType.getConstructor(EndpointURL.class);
            final String resolvedUrl = endpointURLPropertyResolver.resolve(typedSourceDefinition.getUrl());
            EndpointURL endpointURL = EndpointURL.parse(resolvedUrl);
            return ctr.newInstance(endpointURL);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            ReflectionUtils.rethrowRuntimeException(e);
            return null;
        }
    }

}
