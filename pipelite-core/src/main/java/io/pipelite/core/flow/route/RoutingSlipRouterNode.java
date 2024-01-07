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

public class RoutingSlipRouterNode extends AbstractFlowNode implements PipeliteContextAware {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final FlowNode processor;

    private PipeliteContext pipeliteContext;

    public RoutingSlipRouterNode(FlowNode processor) {
        Preconditions.notNull(processor, "processor is required and cannot be null");
        this.processor = processor;
    }

    @Override
    public void process(Exchange exchange) {

        processor.process(exchange);

        exchange.tryGetRoutingSlip().ifPresent(routingSlip -> {

            if(routingSlip.hasNext()){

                final String route = routingSlip.nextRoute();
                if(logger.isDebugEnabled()){
                    logger.debug("{} property found, redirecting flow to '{}'", IOKeys.ROUTING_SLIP_PROPERTY_NAME, route);
                }
                pipeliteContext.supplyExchange(route, exchange);
            }

        });

    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
