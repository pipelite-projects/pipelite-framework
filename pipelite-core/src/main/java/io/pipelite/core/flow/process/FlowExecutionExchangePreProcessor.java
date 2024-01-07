package io.pipelite.core.flow.process;

import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import io.pipelite.spi.flow.process.FlowExecutionContext;

public class FlowExecutionExchangePreProcessor implements ExchangePreProcessor {

    @Override
    public void preProcess(FlowExecutionContext ctx, Exchange exchange) {
        exchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME, ctx.getFlowName());
        exchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME, ctx.getSourceEndpointResource());
    }

}
