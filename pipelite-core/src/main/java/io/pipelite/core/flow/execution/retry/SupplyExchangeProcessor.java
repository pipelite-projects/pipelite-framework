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
import io.pipelite.core.flow.execution.dump.SerializedFlowExecutionDump;
import io.pipelite.core.support.serialization.BaseEncoding;
import io.pipelite.core.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.dsl.route.RoutingSlip;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;

public class SupplyExchangeProcessor extends AbstractFlowNode implements PipeliteContextAware {

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

        final String endpointURL = String.format("link://%s", executionDump.getSourceEndpointResource());
        pipeliteContext.supplyExchange(endpointURL, recoveredExchange);

    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
