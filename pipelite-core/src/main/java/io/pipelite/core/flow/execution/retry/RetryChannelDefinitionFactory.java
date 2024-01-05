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
package io.pipelite.core.flow.execution.retry;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.definition.EndpointDefinitionImpl;
import io.pipelite.core.definition.FlowDefinitionImpl;
import io.pipelite.core.definition.ProcessorDefinitionImpl;
import io.pipelite.core.definition.TypedSourceDefinitionImpl;
import io.pipelite.core.definition.builder.Builder;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.process.DefaultProcessorNode;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.exchange.FlowNode;

public class RetryChannelDefinitionFactory {

    private final FlowExecutionDumpRepository dumpRepository;

    public RetryChannelDefinitionFactory(FlowExecutionDumpRepository dumpRepository) {
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");
        this.dumpRepository = dumpRepository;
    }

    public FlowDefinition createDefinition(String retryChannelName){

        Preconditions.hasText(retryChannelName, "Invalid retryChannelName provided");

        final Builder<FlowDefinitionImpl> builder = Builder.forType(FlowDefinitionImpl.class);
        builder.constructWith(retryChannelName);

        setSourceDefinition(builder, retryChannelName);
        addDefaultProcessorNodeDefinition(builder, "resolve-execution-dump", new ResolveExecutionDumpProcessor(dumpRepository));
        addDefaultProcessorNodeDefinition(builder, "retry-strategy-filter", new RetryStrategyFilter());
        addFlowNodeDefinition(builder, "supply-exchange", new SupplyExchangeProcessor());

        return builder.build();

    }

    private static void setSourceDefinition(Builder<FlowDefinitionImpl> builder, String channelName){
        builder.with(t -> t.setSourceDefinition(new TypedSourceDefinitionImpl(channelName, RetryEndpoint.class)));
    }

    private static void addDefaultProcessorNodeDefinition(Builder<FlowDefinitionImpl> builder, String processorName, Processor processor){
        addFlowNodeDefinition(builder, processorName, new DefaultProcessorNode(processor));
    }

    private static void addFlowNodeDefinition(Builder<FlowDefinitionImpl> builder, String processorName, FlowNode flowNode){
        builder.with(t -> t.addProcessorDefinition(new ProcessorDefinitionImpl(processorName, flowNode)));
    }

    /*
    private static void setEndpointDefinition(Builder<FlowDefinitionImpl> builder, String endpointURL){
        builder.with(t -> t.setEndpointDefinition(new EndpointDefinitionImpl(endpointURL)));
    }*/
}
