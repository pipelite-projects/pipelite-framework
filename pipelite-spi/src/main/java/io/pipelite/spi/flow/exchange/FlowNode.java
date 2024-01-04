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
package io.pipelite.spi.flow.exchange;

import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;

public interface FlowNode {

    void process(Exchange exchange);

    void setNext(FlowNode next);
    boolean hasNext();

    void setFlowName(String flowName);
    void setSourceEndpointResource(String sourceEndpointResource);
    void setProcessorName(String processorName);

    void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor);
    void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor);

    default void tag(String tag){}
    default void setExceptionHandler(ExceptionHandler exceptionHandler){}

}
