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
package io.pipelite.core.flow;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.definition.builder.error.RetryChannelBuilder;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpFactory;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

public class RetryChannelExceptionHandler implements ExceptionHandler {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private FlowExecutionDumpFactory executionDumpFactory;
    private FlowExecutionDumpRepository dumpRepository;

    // Config, not shared infra: plain assignment (not idempotent-guarded like the two fields
    // above) is fine — set once by FlowDefinitionBuilder.build() from user DSL config, never
    // re-set afterward.
    private int maxAttempts = RetryChannelBuilder.DEFAULT_MAX_ATTEMPTS;
    private String deadLetterFlowName;

    public RetryChannelExceptionHandler() {
    }

    public void setExecutionDumpFactory(FlowExecutionDumpFactory executionDumpFactory) {
        if(this.executionDumpFactory == null){
            this.executionDumpFactory = executionDumpFactory;
        }
    }

    public void setDumpRepository(FlowExecutionDumpRepository dumpRepository) {
        if(this.dumpRepository == null) {
            this.dumpRepository = dumpRepository;
        }
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setDeadLetterFlowName(String deadLetterFlowName) {
        this.deadLetterFlowName = deadLetterFlowName;
    }

    @Override
    public void handleException(Throwable failureException, Exchange exchange) {

        Preconditions.notNull(executionDumpFactory, "executionDumpFactory is required and cannot be null");
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");

        exchange.putHeader(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME, failureException.getClass());
        exchange.putHeader(IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME, failureException.getMessage());

        // executionDumpFactory.create(...) already reads FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME
        // off the exchange and sets attemptNumber+1 on the dump it returns — a previous version of
        // this method re-read the same (un-incremented) property afterward and called
        // executionDump.setAttemptNumber(attemptNumber) here, silently overwriting the correct
        // increment. That bug meant attemptNumber never actually advanced across retries, so
        // RetryStrategyFilter's cap never triggered. Fixed by not re-setting it.
        final FlowExecutionDump executionDump = executionDumpFactory.create(failureException, exchange);
        executionDump.setStackTrace(formatStackTrace(failureException));
        executionDump.setMaxAttempts(maxAttempts);
        executionDump.setDeadLetterFlowName(deadLetterFlowName);

        final String executionDumpId = executionDump.getId();
        exchange.setProperty(IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME, executionDumpId);

        dumpRepository.save(executionDump);

    }

    private static String formatStackTrace(Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

}
