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
package io.pipelite.dsl.definition;

public interface ProcessorDefinition {

    <T> T getProcessor(Class<T> processorType);
    String getProcessorName();

    /**
     * Mirrors {@link FlowDefinition#getExceptionHandler(Class)} exactly. Generic (rather than
     * simply returning {@code io.pipelite.dsl.process.ExceptionHandler}) because callers also
     * retrieve concrete {@code pipelite-core} implementations by their own type (e.g.
     * {@code RetryChannelExceptionHandler.class}) to inject collaborators - see {@code
     * DefaultPipeliteContext#registerFlows()}. The setter counterpart is not part of this
     * interface; it lives only on the concrete {@code pipelite-core} implementation, exactly like
     * {@code FlowDefinitionImpl#setExceptionHandler}.
     */
    <T> T getExceptionHandler(Class<T> expectedType);

}
