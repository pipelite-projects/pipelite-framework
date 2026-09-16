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
package io.pipelite.core.definition;

import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.dsl.definition.ProcessorDefinition;
import io.pipelite.spi.flow.ExceptionHandler;

public class ProcessorDefinitionImpl implements ProcessorDefinition {

    private final String processorName;
    private final FlowNode flowNode;

    private ExceptionHandler exceptionHandler;

    public ProcessorDefinitionImpl(String processorName, FlowNode flowNode) {
        this.processorName = processorName;
        this.flowNode = flowNode;
    }

    @Override
    public <T> T getProcessor(Class<T> processorType) {
        if(processorType.isAssignableFrom(flowNode.getClass())){
            return processorType.cast(flowNode);
        }
        throw new ClassCastException(String.format("Unable to cast from %s to %s", flowNode.getClass(), processorType));
    }

    @Override
    public String getProcessorName() {
        return processorName;
    }

    @Override
    public <T> T getExceptionHandler(Class<T> expectedType) {
        if (exceptionHandler != null) {
            if (expectedType.isAssignableFrom(exceptionHandler.getClass())) {
                return expectedType.cast(exceptionHandler);
            }
            throw new ClassCastException(String.format("Unable to cast from %s to %s", exceptionHandler.getClass(), expectedType));
        }
        return null;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
}
