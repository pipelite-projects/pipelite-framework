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
package io.pipelite.core.flow.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.context.internal.DeclaredDestination;
import io.pipelite.core.context.internal.DeclaresDestinations;
import io.pipelite.core.flow.ExpressionVariables;
import io.pipelite.core.flow.expression.TextExpressionEvaluator;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RouteEntry;
import io.pipelite.dsl.route.RoutingTable;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Package-private since #82: construct via {@link RouteNodeFactory#router(RoutingTable,
 * TextExpressionEvaluator)}.
 */
class RouterNode extends AbstractFlowNode implements PipeliteContextAware, FlowExitNode, DeclaresDestinations {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final RoutingTable<?> routingTable;
    private final TextExpressionEvaluator textExpressionEvaluator;

    private PipeliteContext pipeliteContext;

    RouterNode(RoutingTable<?> routingTable, TextExpressionEvaluator textExpressionEvaluator) {
        this.routingTable = Preconditions.notNull(routingTable, "routingTable is required and cannot be null");
        this.textExpressionEvaluator = Preconditions.notNull(textExpressionEvaluator, "textExpressionEvaluator is required and cannot be null");
    }

    /**
     * A failure while routing - a destination that cannot be delivered, no route and no default
     * route, an expression that cannot be evaluated - is handed to the flow's exception handler
     * like a failed processor step (issue #105), so {@code withRetry}, {@code withErrorChannel} and
     * the default handler protect this step too. Routing to several destinations is not atomic: if
     * the second delivery fails after the first went through, a retry of this step sends to the
     * first one again (at-least-once, as everywhere else in the framework).
     */
    @Override
    public void process(ExchangeImpl exchange) {
        try {
            route(exchange);
        } catch (RuntimeException exception) {
            if (sysLogger.isErrorEnabled()) {
                sysLogger.error("An underlying error occurred routing message", exception);
            }
            handleFailure(exception, exchange);
        }
    }

    private void route(ExchangeImpl exchange) {

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
    public Collection<DeclaredDestination> declaredDestinations() {
        final List<DeclaredDestination> destinations = new ArrayList<>();
        for (RouteEntry<?> entry : routingTable) {
            entry.getDestination().forEach(destination -> destinations.add(new DeclaredDestination(destination, "toRoute(...)")));
        }
        routingTable.getDefaultRoutes().ifPresent(defaults ->
            defaults.forEach(destination -> destinations.add(new DeclaredDestination(destination, "toRoute(...)"))));
        return destinations;
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
