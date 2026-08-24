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
package io.pipelite.core.flow;

import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.Optional;

/**
 * Locates a {@link FlowNode} by processor name within a single {@link Flow}'s own node chain.
 * A {@code Flow}'s chain is a plain linear {@code next}-linked list — routing constructs
 * (recipient list, routing slip, return address, {@code link://} handoff) all dispatch to a
 * <em>different</em> registered flow/endpoint via {@code PipeliteContext#supplyExchange}, they
 * never fork the same flow's own chain — so a linear scan is sufficient and unambiguous,
 * provided processor names are unique within the flow (enforced at flow-definition build time).
 */
public final class FlowNodeLocator {

    private FlowNodeLocator(){
    }

    public static Optional<FlowNode> findByProcessorName(Flow flow, String processorName){
        if (flow == null || processorName == null) {
            return Optional.empty();
        }
        FlowNode current = flow.getConsumer();
        while (current != null) {
            if (processorName.equals(current.getProcessorName())) {
                return Optional.of(current);
            }
            current = current.getNext();
        }
        return Optional.empty();
    }

}
