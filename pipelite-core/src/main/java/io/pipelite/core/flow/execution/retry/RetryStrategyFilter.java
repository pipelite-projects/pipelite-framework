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

import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;

public class RetryStrategyFilter implements Processor {

    private final int MAX_ATTEMPTS = 3;

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final FlowExecutionDump executionDump = ioContext.getInputPayloadAs(FlowExecutionDump.class);
        if(executionDump.getAttemptNumber() > MAX_ATTEMPTS){
            contribution.stopExecution();
        }

    }

}
