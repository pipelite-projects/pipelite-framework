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

import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public non-sealed class DefaultProducer extends AbstractProducer implements Producer {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    public DefaultProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public final void setNext(FlowNode next) {
        throw new IllegalStateException("A producer does not have a next node link.");
    }

    @Override
    public final boolean hasNext() {
        return false;
    }

    /**
     * Final since issue #89: previously every concrete producer overrode {@code process(Exchange)}
     * directly, so none of them ever consulted {@code exceptionHandler} - a producer's own failure
     * (e.g. a broker down, a file write failing) always propagated uncaught, bypassing retry/
     * dead-letter channels entirely, unlike a processor step's failure, which {@code
     * AbstractProcessorNode} already routes through the same handler. Mirrors {@code
     * AbstractProcessorNode#process}'s exact shape so a producer failure is handled identically to
     * a processor failure. Concrete producers implement {@link #doProcess(ExchangeImpl)} instead.
     */
    @Override
    public final void process(ExchangeImpl exchange) {
        try {
            doProcess(exchange);
        } catch (RuntimeException exception) {
            if (sysLogger.isErrorEnabled()) {
                sysLogger.error("An underlying error occurred producing message", exception);
            }
            if (exceptionHandler != null) {
                exchange.setProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, getProcessorName());
                exceptionHandler.handleException(exception, exchange);
            } else {
                throw exception;
            }
        }
    }

    public void doProcess(ExchangeImpl exchange) {
    }
}
