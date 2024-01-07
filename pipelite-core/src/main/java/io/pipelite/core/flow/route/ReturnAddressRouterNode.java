package io.pipelite.core.flow.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReturnAddressRouterNode extends AbstractFlowNode implements PipeliteContextAware {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final FlowNode processor;

    private PipeliteContext pipeliteContext;

    public ReturnAddressRouterNode(FlowNode processor) {
        Preconditions.notNull(processor, "processor is required and cannot be null");
        this.processor = processor;
    }

    @Override
    public void process(Exchange exchange) {

        processor.process(exchange);

        exchange.tryGetReturnAddress().ifPresent(flowName -> {
            if(logger.isDebugEnabled()){
                logger.debug("{} header found, redirecting flow to '{}'", IOKeys.RETURN_ADDRESS_HEADER_NAME, flowName);
            }
            exchange.resetReturnAddress();
            pipeliteContext.supplyExchange(flowName, exchange);
        });

    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
