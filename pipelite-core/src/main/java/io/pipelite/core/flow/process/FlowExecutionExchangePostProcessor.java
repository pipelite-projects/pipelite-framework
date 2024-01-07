package io.pipelite.core.flow.process;

import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.FlowExecutionContext;

public class FlowExecutionExchangePostProcessor implements ExchangePostProcessor {

    @Override
    public void postProcess(FlowExecutionContext ctx, Exchange exchange) {
        exchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME, ctx.getProcessorName());
    }

}
