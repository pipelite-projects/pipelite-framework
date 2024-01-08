package io.pipelite.traces;

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import io.pipelite.spi.flow.process.FlowExecutionContext;

public class OTelSpanExchangePostProcessor implements ExchangePostProcessor

    @Override
    public void postProcess(FlowExecutionContext ctx, Exchange exchange) {

    }

    @Override
    public int getOrder() {
        return ExchangePreProcessor.LOWEST_PRECEDENCE;
    }
}
