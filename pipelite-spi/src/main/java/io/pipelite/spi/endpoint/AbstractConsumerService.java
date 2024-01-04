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
package io.pipelite.spi.endpoint;

import io.pipelite.spi.context.AbstractService;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.Objects;

public abstract class AbstractConsumerService extends AbstractService implements Consumer {

    protected final Consumer consumer;

    public AbstractConsumerService(Consumer consumer) {
        Objects.requireNonNull(consumer, "consumer is required and cannot be null");
        this.consumer = consumer;
    }

    @Override
    public Endpoint getEndpoint() {
        return consumer.getEndpoint();
    }

    @Override
    public void consume(Exchange exchange) {
        consumer.consume(exchange);
    }

    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        consumer.setExceptionHandler(exceptionHandler);
    }

    @Override
    public void tag(String tag) {
        consumer.tag(tag);
    }

    @Override
    public void process(Exchange exchange) {
        consumer.process(exchange);
    }

    @Override
    public void setNext(FlowNode next) {
        consumer.setNext(next);
    }

    @Override
    public boolean hasNext() {
        return consumer.hasNext();
    }
}
