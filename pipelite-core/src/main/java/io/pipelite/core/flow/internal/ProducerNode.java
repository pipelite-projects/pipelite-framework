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
package io.pipelite.core.flow.internal;

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The node at the end of a flow that writes to a sink: it wraps the {@link Producer} an adapter
 * provides, which has no idea it is a node of a flow, and applies the flow's error handling around
 * {@link Producer#produce}.
 * <p>
 * The shape is the one a producer had since issue #89, when it became {@code final} on the old base
 * class: previously every concrete producer overrode {@code process(Exchange)} directly, so none of them
 * ever consulted the exception handler and a producer's own failure (a broker down, a file write
 * failing) always propagated uncaught, bypassing the retry and dead-letter channels, unlike a
 * processor step's failure. A failure of {@code produce} is handled like the failure of a processor;
 * with no handler set it propagates.
 */
public final class ProducerNode extends AbstractFlowNode {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Producer producer;

    public ProducerNode(Producer producer) {
        this.producer = Preconditions.notNull(producer, "producer is required and cannot be null");
    }

    @Override
    public void setNext(FlowNode next) {
        throw new IllegalStateException("A producer does not have a next node link.");
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public void process(ExchangeImpl exchange) {
        try {
            producer.produce(exchange);
        } catch (RuntimeException exception) {
            if (sysLogger.isErrorEnabled()) {
                sysLogger.error("An underlying error occurred producing message", exception);
            }
            handleFailure(exception, exchange);
        }
    }
}
