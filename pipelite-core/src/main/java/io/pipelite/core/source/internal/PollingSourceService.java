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
package io.pipelite.core.source.internal;

import io.pipelite.dsl.Exchange;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.core.consumer.internal.ScheduledPollingConsumerService;
import io.pipelite.core.concurrent.internal.DefaultThreadFactory;
import io.pipelite.core.concurrent.internal.FlowNameAbbreviator;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.source.PollingSource;
import io.pipelite.spi.source.SourceContext;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/** The consumer of a flow fed by a {@link PollingSource}: the schedule is the framework's. */
final class PollingSourceService extends ScheduledPollingConsumerService implements ExchangeFactoryAware {

    private final PollingSource source;
    private final PollingSourceConsumer consumer;
    private final ThreadFactory threadFactory;

    static PollingSourceService create(PollingSource source, Endpoint endpoint, String role) {
        final PollingSourceConsumer consumer = new PollingSourceConsumer(endpoint, source);
        final ThreadFactory threadFactory = new DefaultThreadFactory(role,
            () -> FlowNameAbbreviator.abbreviate(consumer.getFlowName()));
        return new PollingSourceService(source, consumer, threadFactory,
            Executors.newSingleThreadScheduledExecutor(threadFactory));
    }

    private PollingSourceService(PollingSource source, PollingSourceConsumer consumer, ThreadFactory threadFactory,
                                 ScheduledExecutorService consumerPool) {
        super(consumer, consumerPool);
        this.source = source;
        this.consumer = consumer;
        this.threadFactory = threadFactory;
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        consumer.setExchangeFactory(exchangeFactory);
    }

    @Override
    public void doStart() {
        source.start(new Context());
        super.doStart();
    }

    @Override
    public void doStop() {
        super.doStop();
        source.stop();
    }

    private final class Context implements SourceContext {

        @Override
        public String flowName() {
            return consumer.getFlowName();
        }

        @Override
        public Exchange newExchange(Object payload) {
            return consumer.newExchange(payload);
        }

        @Override
        public void accept(Exchange exchange) {
            consumer.consume(SourceExchanges.asExchangeImpl(exchange));
        }

        @Override
        public ThreadFactory threadFactory() {
            return threadFactory;
        }

        @Override
        public boolean isRunning() {
            return isRunAllowed();
        }
    }
}
