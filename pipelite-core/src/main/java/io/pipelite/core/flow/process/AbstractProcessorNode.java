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
package io.pipelite.core.flow.process;

import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.flow.exchange.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public abstract class AbstractProcessorNode extends AbstractFlowNode implements FlowNode, ExchangeFactoryAware {

    public interface WrapDelegateCallback {
        Processor doWithDelegate(Processor delegate);
    }

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private Processor delegate;

    private ExchangeFactory exchangeFactory;

    public AbstractProcessorNode(Processor delegate) {
        Objects.requireNonNull(delegate, "delegate is required and cannot be null");
        this.delegate = delegate;
    }

    public void wrapDelegate(WrapDelegateCallback callback){
        Objects.requireNonNull(callback, "callback is required and cannot be null");
        this.delegate = Objects.requireNonNull(callback.doWithDelegate(delegate), "delegate is required and cannot be null");
    }

    @Override
    public void process(Exchange exchange) {

        final ProcessContribution contribution = new ProcessContributionImpl();

        try {
            preProcessExchange(exchange);
            delegate.process(exchange, contribution);
            postProcessExchange(exchange);
            if(tag != null && sysLogger.isTraceEnabled()){
                sysLogger.trace("{} - Exchange successfully processed", tag);
            }
        }catch(RuntimeException exception) {
            if(sysLogger.isErrorEnabled()){
                sysLogger.error("An underlying error occurred processing message", exception);
            }
            if(exceptionHandler != null){
                exceptionHandler.handleException(exception, exchange);
                return;
            }else{
                throw exception;
            }
        }

        if(next != null && !contribution.isExecutionStopped()){
            final Exchange nextExchange = exchangeFactory.nextExchange(exchange);
            next.process(nextExchange);
        }
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

}
