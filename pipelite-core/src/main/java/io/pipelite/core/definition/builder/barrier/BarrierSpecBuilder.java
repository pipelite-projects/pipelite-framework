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
package io.pipelite.core.definition.builder.barrier;

import io.pipelite.core.context.internal.ChannelURL;
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.dsl.barrier.BarrierOperations;
import io.pipelite.dsl.barrier.Correlation;
import io.pipelite.dsl.barrier.CorrelationConfigurator;
import io.pipelite.spi.endpoint.EndpointURL;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The implementation of {@link BarrierOperations}: collects what the user declared and, in {@link #build()},
 * checks it, so a barrier that could never work is refused when the flow is defined. What can only be known once
 * every flow is registered (that an awaited flow exists, that no flow waits for itself) is checked when the
 * context starts.
 */
public final class BarrierSpecBuilder implements BarrierOperations {

    private record Declared(String flowURL, String queueName, Correlation own) {
    }

    private Correlation shared;
    private final List<Declared> declared = new ArrayList<>();
    private Duration timeout;

    @Override
    public BarrierOperations correlateBy(CorrelationConfigurator correlation) {
        this.shared = configure(correlation);
        return this;
    }

    @Override
    public BarrierOperations await(String flowURL) {
        declared.add(new Declared(flowURL, queueNameOf(flowURL), null));
        return this;
    }

    @Override
    public BarrierOperations await(String flowURL, CorrelationConfigurator correlation) {
        declared.add(new Declared(flowURL, queueNameOf(flowURL), configure(correlation)));
        return this;
    }

    @Override
    public BarrierOperations timeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout is required and cannot be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("the timeout of a barrier must be positive, got " + timeout);
        }
        this.timeout = timeout;
        return this;
    }

    /** Checks what was declared and returns it as an immutable {@link BarrierSpec}. */
    public BarrierSpec build() {
        if (declared.isEmpty()) {
            throw new IllegalArgumentException("a barrier needs at least one await: the flow whose completion it waits for");
        }
        if (timeout == null) {
            throw new IllegalArgumentException("a barrier needs a timeout: without one an exchange whose flow never completes waits forever");
        }
        final Set<String> seen = new HashSet<>();
        final List<BarrierSpec.Await> awaits = new ArrayList<>();
        for (Declared await : declared) {
            if (!seen.add(await.queueName())) {
                throw new IllegalArgumentException(String.format("the flow that reads '%s' is awaited twice", await.flowURL()));
            }
            final Correlation correlation = await.own() != null ? await.own() : shared;
            if (correlation == null) {
                throw new IllegalArgumentException(String.format(
                    "await(\"%s\") has no key: give it one, or declare correlateBy for the barrier", await.flowURL()));
            }
            awaits.add(new BarrierSpec.Await(await.flowURL(), await.queueName(), correlation));
        }
        return new BarrierSpec(awaits, timeout);
    }

    private static Correlation configure(CorrelationConfigurator configurator) {
        Objects.requireNonNull(configurator, "correlation is required and cannot be null");
        return Objects.requireNonNull(configurator.configure(new CorrelationBuilder()),
            "the correlation must be the one the CorrelationOperations it is given returns");
    }

    /**
     * A flow is referenced by the URL of the queue it reads, the way flows are linked everywhere else: one flow
     * reads a queue, which is what makes the URL identify it. A bare name, or the URL of another kind of
     * source, is refused.
     */
    private static String queueNameOf(String flowURL) {
        if (flowURL == null || flowURL.isBlank()) {
            throw new IllegalArgumentException("await needs the URL of the flow, as queue://<queue it reads>");
        }
        final ChannelURL channelURL = ChannelURL.parse(flowURL);
        if (!channelURL.hasProtocol()) {
            throw new IllegalArgumentException(String.format(
                "await(\"%s\"): '%s' is not a URL - flows are addressed by URL, write '%s' to wait for the flow that reads the queue '%s'",
                flowURL, flowURL, ChannelProtocols.queueURL(flowURL), flowURL));
        }
        if (!ChannelProtocols.QUEUE.equals(channelURL.getProtocol())) {
            throw new IllegalArgumentException(String.format(
                "await(\"%s\"): only a flow that reads a queue (queue://<name>) can be awaited, not one that reads from '%s'",
                flowURL, channelURL.getProtocol()));
        }
        final String queueName = EndpointURL.parse(channelURL.getEndpointURL()).getResource();
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException(String.format("await(\"%s\"): the URL names no queue", flowURL));
        }
        return queueName;
    }
}
