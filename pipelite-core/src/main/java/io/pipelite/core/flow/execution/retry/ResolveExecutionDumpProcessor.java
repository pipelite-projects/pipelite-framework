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

/**
 * Resolves the {@link FlowExecutionDump} a retry-channel exchange carries (or, failing that,
 * loads it by id) and makes it the exchange's payload. Deliberately does <strong>not</strong>
 * remove it from {@code dumpRepository} — doing so here, before the retry attempt has actually
 * run, would leave a message with no durable record of it at all for the entire duration of the
 * attempt (issue #58): a crash between this resolution and the attempt's own outcome (success, or
 * a new dump saved for a further attempt) would lose it silently, exactly the class of gap #68
 * closed for the "waiting to be retried" window but not for "actively being retried". Removal now
 * happens later, only once an outcome is actually known — see {@link RetryStrategyFilter} (attempts
 * exhausted) and {@link SupplyExchangeProcessor} (resupply attempted).
 */
public class ResolveExecutionDumpProcessor implements Processor {

    private final FlowExecutionDumpRepository dumpRepository;

    public ResolveExecutionDumpProcessor(FlowExecutionDumpRepository dumpRepository) {
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");
        this.dumpRepository = dumpRepository;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final Exchange exchange = (Exchange)ioContext;

        final FlowExecutionDump flowExecutionDump = exchange.getInputPayloadAs(FlowExecutionDump.class);

        if(flowExecutionDump == null){

            final String executionDumpId = exchange.getProperty(IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME, String.class);
            Preconditions.notNull(executionDumpId, String.format("Exchange property %s is required and cannot be null",
                IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME));

            final FlowExecutionDump loaded = dumpRepository.tryLoad(executionDumpId)
                .orElseThrow(() -> new IllegalStateException(String.format("Unrecognized flow-execution-dump id '%s'", executionDumpId)));
            ioContext.setOutputPayload(loaded);

        }

    }

}
