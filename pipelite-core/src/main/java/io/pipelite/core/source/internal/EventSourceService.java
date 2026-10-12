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
import io.pipelite.spi.endpoint.EventDrivenConsumer;
import io.pipelite.spi.endpoint.EventDrivenConsumerService;
import io.pipelite.spi.source.EventSource;
import io.pipelite.spi.source.SourceContext;

import java.util.concurrent.ThreadFactory;

/** The consumer of a flow fed by an {@link EventSource}: it starts the source once the machinery is running. */
final class EventSourceService extends EventDrivenConsumerService {

    private final EventSource source;
    private String flowName;

    EventSourceService(EventSource source, Endpoint endpoint, String role) {
        super(new EventDrivenConsumer(endpoint), role);
        this.source = source;
    }

    @Override
    public void setFlowName(String flowName) {
        this.flowName = flowName;
        super.setFlowName(flowName);
    }

    @Override
    public void doStart() {
        super.doStart();
        source.start(new Context());
    }

    @Override
    public void doStop() {
        source.stop();
        super.doStop();
    }

    private final class Context implements SourceContext {

        @Override
        public String flowName() {
            return flowName;
        }

        @Override
        public Exchange newExchange(Object payload) {
            return exchangeFactory.createExchange(payload);
        }

        @Override
        public void accept(Exchange exchange) {
            process(SourceExchanges.asExchangeImpl(exchange));
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
