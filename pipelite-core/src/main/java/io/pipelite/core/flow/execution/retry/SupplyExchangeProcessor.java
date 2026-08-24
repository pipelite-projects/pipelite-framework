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

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.flow.FlowNodeLocator;
import io.pipelite.core.flow.execution.dump.SerializedFlowExecutionDump;
import io.pipelite.core.support.serialization.BaseEncoding;
import io.pipelite.core.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resumes a failed flow's execution. Prefers resuming directly at the {@code FlowNode} that
 * actually failed ({@link SerializedFlowExecutionDump#getFailedProcessor()}) — bypassing the
 * flow's source and every already-succeeded step, which would otherwise re-run and could
 * re-apply side effects. Falls back to the pre-existing behavior (resupply from the flow's own
 * source) only when the failed processor's identity or the flow itself can't be resolved, e.g.
 * a dump produced before this resume mechanism existed, or a failure that didn't originate
 * inside a processor's own catch block.
 */
public class SupplyExchangeProcessor extends AbstractFlowNode implements PipeliteContextAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final ByteArrayToObjectConverter converter;

    private PipeliteContext pipeliteContext;

    public SupplyExchangeProcessor() {
        converter = new ByteArrayToObjectConverter();
    }

    @Override
    public void process(Exchange exchange) {

        final SerializedFlowExecutionDump executionDump = exchange.getInputPayloadAs(SerializedFlowExecutionDump.class);

        final String exchangeData = executionDump.getExchangeData();
        final byte[] exchangeContent = BaseEncoding.base64().decode(exchangeData);

        final Exchange recoveredExchange = converter.convert(exchangeContent, Exchange.class);
        recoveredExchange.setProperty(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, executionDump.getAttemptNumber());
        recoveredExchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME, executionDump.getLastExecutedProcessor());

        final String failedProcessor = executionDump.getFailedProcessor();
        if (failedProcessor != null) {
            final FlowNode target = pipeliteContext.tryFindFlow(executionDump.getSourceEndpointResource())
                .flatMap(flow -> FlowNodeLocator.findByProcessorName(flow, failedProcessor))
                .orElse(null);
            if (target != null) {
                target.process(recoveredExchange);
                return;
            }
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("Unable to resolve failed processor '{}' on flow source '{}' for FlowExecutionDump {}, " +
                        "falling back to source resupply",
                    failedProcessor, executionDump.getSourceEndpointResource(), executionDump.getId());
            }
        } else if (sysLogger.isWarnEnabled()) {
            sysLogger.warn("FlowExecutionDump {} has no failedProcessor, falling back to source resupply",
                executionDump.getId());
        }

        final String endpointURL = String.format("link://%s", executionDump.getSourceEndpointResource());
        pipeliteContext.supplyExchange(endpointURL, recoveredExchange);

    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
