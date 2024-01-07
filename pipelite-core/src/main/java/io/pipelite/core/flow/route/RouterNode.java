package io.pipelite.core.flow.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.flow.ExpressionVariables;
import io.pipelite.core.flow.expression.TextExpressionEvaluator;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RoutingTable;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RouterNode extends AbstractFlowNode implements PipeliteContextAware {

    private final RoutingTable<?> routingTable;
    private final TextExpressionEvaluator textExpressionEvaluator;

    private PipeliteContext pipeliteContext;

    public RouterNode(RoutingTable<?> routingTable, TextExpressionEvaluator textExpressionEvaluator) {
        this.routingTable = Preconditions.notNull(routingTable, "routingTable is required and cannot be null");
        this.textExpressionEvaluator = Preconditions.notNull(textExpressionEvaluator, "textExpressionEvaluator is required and cannot be null");
    }

    @Override
    public void process(Exchange exchange) {

        final Optional<RecipientList> routeHolder = routingTable.resolveRoute(exchange);

        if(routeHolder.isPresent()){

            RecipientList destinations = routeHolder.get();

            destinations.forEach(destination -> {

                if(textExpressionEvaluator.containsExpressions(destination)){
                    final Map<String,Object> variables = new ConcurrentHashMap<>();
                    variables.put(ExpressionVariables.PAYLOAD_VARIABLE_NAME, exchange.getInputPayload());
                    variables.put(ExpressionVariables.HEADERS_VARIABLE_NAME, exchange.getHeaders());
                    destination = textExpressionEvaluator.evaluateText(destination, variables);
                }

                try{
                    Objects.requireNonNull(pipeliteContext, "pipeliteContext not set, it's required and cannot be null");
                    pipeliteContext.supplyExchange(destination, exchange);
                }catch (RuntimeException exception){
                    throw new IllegalStateException(String.format("Unable to forward exchange on route-name '%s' due to an underlying error", destination), exception);
                }

            });
        } else {
            throw new IllegalStateException("Unable to route exchange, unresolved route name. Have you set the default route?");
        }

    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
