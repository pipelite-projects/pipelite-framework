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

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.dsl.route.RoutingSlip;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The end of every flow (issue #86): where a Routing Slip is followed. Reached only when the
 * flow's steps finished successfully and none stopped the execution, so a failed or filtered
 * exchange never hops. One rule, whatever the flow's own exit is: if the exchange carries a
 * {@link RoutingSlip} with routes left it hops to the next one and the flow's own exit does not
 * run; if not, the flow exits normally.
 * <p>
 * Two placements, both built through {@link RouteNodeFactory}:
 * <ul>
 *     <li>in front of an exit node - the producer of a {@code toSink(...)}, or a {@link
 *     FlowExitNode} ({@code toRoute}/{@code toRecipientList}) - which then runs only when there
 *     is no slip to follow;</li>
 *     <li>at the very end of a flow with no exit of its own, where the natural exit is the
 *     return address: it replies only once the slip is exhausted (or was never set), never in
 *     addition to a hop, since the next flow's {@code consume(...)} is asynchronous and the same
 *     exchange would otherwise reach two flows at once.</li>
 * </ul>
 */
class RoutingSlipGateNode extends AbstractFlowNode implements PipeliteContextAware {

    static final String PROCESSOR_NAME = "$routing-slip";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final boolean repliesToReturnAddress;

    private PipeliteContext pipeliteContext;

    RoutingSlipGateNode(boolean repliesToReturnAddress) {
        this.repliesToReturnAddress = repliesToReturnAddress;
    }

    @Override
    public void process(Exchange exchange) {

        final Optional<RoutingSlip> slip = exchange.tryGetRoutingSlip().filter(RoutingSlip::hasNext);
        if (slip.isPresent()) {
            final String route = slip.get().nextRoute();
            if (logger.isDebugEnabled()) {
                logger.debug("{} found, redirecting flow to '{}'", IOKeys.ROUTING_SLIP_PROPERTY_NAME, route);
            }
            deliver(route, exchange, () -> slip.get().restoreRoute(route));
            return;
        }

        if (repliesToReturnAddress) {
            exchange.tryGetReturnAddress().ifPresent(returnAddress -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} header found, redirecting flow to '{}'", IOKeys.RETURN_ADDRESS_HEADER_NAME, returnAddress);
                }
                exchange.resetReturnAddress();
                deliver(returnAddress, exchange, () -> exchange.setReturnAddress(returnAddress));
            });
        }

        if (next != null) {
            next.process(exchange);
        }
    }

    /**
     * A failed delivery (most commonly a route that matches no registered flow) is handled like a
     * failed step: reported through the flow's exception handler with this gate as the failed
     * processor, so a retry resumes right here. {@code undo} puts back what was consumed for the
     * attempt, so that resumed attempt finds the same route (or return address) again instead of
     * silently skipping it.
     */
    private void deliver(String target, Exchange exchange, Runnable undo) {
        try {
            pipeliteContext.supplyExchange(target, exchange);
        } catch (RuntimeException exception) {
            undo.run();
            final IllegalStateException failure = new IllegalStateException(String.format(
                "Unable to forward exchange of flow '%s' to '%s' - is there a flow declaring fromSource('%s')?",
                getFlowName(), target, target), exception);
            if (logger.isErrorEnabled()) {
                logger.error("An underlying error occurred forwarding message", failure);
            }
            if (exceptionHandler != null) {
                exchange.setProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, getProcessorName());
                exceptionHandler.handleException(failure, exchange);
                return;
            }
            throw failure;
        }
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
