package io.pipelite.core.flow.process;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;

public class WireTapProcessorNode extends AbstractProcessorNode implements PipeliteContextAware {

    private static final class NoOpProcessor implements Processor {
        @Override
        public void process(IOContext ioContext, ProcessContribution contribution) {
        }
    }

    private static final NoOpProcessor NO_OP_PROCESSOR = new NoOpProcessor();

    private final String endpointURL;

    private PipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;

    public WireTapProcessorNode(String endpointURL) {
        super(NO_OP_PROCESSOR);
        this.endpointURL = endpointURL;
    }

    @Override
    public void process(Exchange exchange) {

        pipeliteContext.supplyExchange(endpointURL, exchangeFactory.copyExchange(exchange));
        super.process(exchange);

    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.pipeliteContext = pipeliteContext;
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
    }
}
