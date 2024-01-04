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
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;

public class ResolveExecutionDumpProcessor implements Processor {

    private final FlowExecutionDumpRepository dumpRepository;

    public ResolveExecutionDumpProcessor(FlowExecutionDumpRepository dumpRepository) {
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");
        this.dumpRepository = dumpRepository;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final Exchange exchange = (Exchange)ioContext;

        FlowExecutionDump flowExecutionDump = exchange.getInputPayloadAs(FlowExecutionDump.class);

        if(flowExecutionDump == null){

            final String executionDumpId = exchange.getProperty(IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME, String.class);
            Preconditions.notNull(executionDumpId, String.format("Exchange property %s is required and cannot be null",
                IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME));

            flowExecutionDump = dumpRepository.tryLoad(executionDumpId)
                .orElseThrow(() -> new IllegalStateException(String.format("Unrecognized flow-execution-dump id '%s'", executionDumpId)));
            ioContext.setOutputPayload(flowExecutionDump);

        }

        dumpRepository.remove(flowExecutionDump.getId());

    }

}
