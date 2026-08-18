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
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.dump.SerializedFlowExecutionDump;
import io.pipelite.core.support.serialization.BaseEncoding;
import io.pipelite.core.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Decides whether a failed message has exhausted its configured retry attempts. On exhaustion,
 * routes to the flow's configured dead-letter channel (if any) instead of always dropping the
 * message — see {@code RetryChannelExceptionHandler}, which copies both {@code maxAttempts} and
 * the dead-letter URL onto every {@link FlowExecutionDump} it creates, since this filter is a
 * single instance shared by every flow's dumps and has no other way to know a given dump's
 * owning flow's configuration.
 */
public class RetryStrategyFilter implements Processor {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final PipeliteContext pipeliteContext;
    private final ByteArrayToObjectConverter converter;

    public RetryStrategyFilter(PipeliteContext pipeliteContext) {
        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.pipeliteContext = pipeliteContext;
        this.converter = new ByteArrayToObjectConverter();
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final FlowExecutionDump executionDump = ioContext.getInputPayloadAs(FlowExecutionDump.class);
        if(executionDump.getAttemptNumber() > executionDump.getMaxAttempts()){

            contribution.stopExecution();

            final String deadLetterFlowName = executionDump.getDeadLetterFlowName();
            if(deadLetterFlowName != null){
                final Exchange recoveredExchange = tryDecodeExchange(executionDump);
                if(recoveredExchange == null){
                    if(sysLogger.isWarnEnabled()){
                        sysLogger.warn("FlowExecutionDump {} has no recoverable exchange data, unable to route to dead letter flow '{}'",
                            executionDump.getId(), deadLetterFlowName);
                    }
                } else {
                    final Optional<Flow> deadLetterFlow = pipeliteContext.tryFindFlowByName(deadLetterFlowName);
                    if(deadLetterFlow.isPresent()){
                        deadLetterFlow.get().supply(recoveredExchange);
                    } else if(sysLogger.isWarnEnabled()){
                        sysLogger.warn("Dead letter flow '{}' is not registered, unable to route exchange for FlowExecutionDump {}",
                            deadLetterFlowName, executionDump.getId());
                    }
                }
            }
        }

    }

    private Exchange tryDecodeExchange(FlowExecutionDump executionDump) {
        if(!(executionDump instanceof SerializedFlowExecutionDump)){
            return null;
        }
        final SerializedFlowExecutionDump serialized = (SerializedFlowExecutionDump) executionDump;
        if(serialized.getExchangeData() == null){
            return null;
        }
        final byte[] exchangeContent = BaseEncoding.base64().decode(serialized.getExchangeData());
        return converter.convert(exchangeContent, Exchange.class);
    }

}
