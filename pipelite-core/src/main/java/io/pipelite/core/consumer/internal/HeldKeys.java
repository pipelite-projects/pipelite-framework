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
package io.pipelite.core.consumer.internal;

import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.barrier.BarrierRepository;
import io.pipelite.core.flow.barrier.ExecutionTracker;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.SerializedFlowExecutionDump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The keys of an ordered source (issue #156) that exchanges of the flow held when the process stopped, and still hold:
 * the ones waiting in the retry channel for another attempt, and the ones waiting at a barrier. Their executions have
 * not ended, so the messages that arrive after the restart, the ones the durable inbox gives back first of all, must
 * not overtake them.
 * <p>
 * Read once, when the flow starts. An entry that cannot be read is skipped, with a warning: the order of its key is
 * then not kept against it, but nothing else is lost.
 */
final class HeldKeys {

    private static final Logger SYS_LOGGER = LoggerFactory.getLogger(HeldKeys.class);

    private HeldKeys() {
    }

    static List<ExecutionTracker.Ordering> of(InternalPipeliteContext context, String flowName) {
        final List<ExecutionTracker.Ordering> held = new ArrayList<>();
        final ByteArrayToObjectConverter converter = new ByteArrayToObjectConverter();
        final FlowExecutionDumpRepository dumps = context.getFlowExecutionDumpRepository();
        if (dumps != null) {
            for (FlowExecutionDump dump : dumps.findByFlow(flowName)) {
                if (dump instanceof SerializedFlowExecutionDump serialized) {
                    add(held, flowName, "retry " + dump.getId(), serialized.getExchangeData(), converter);
                }
            }
        }
        final BarrierRepository barriers = context.getBarrierRepository();
        if (barriers != null) {
            barriers.findByFlow(flowName)
                .forEach(waiter -> add(held, flowName, "wait " + waiter.id(), waiter.exchangeData(), converter));
        }
        return held;
    }

    private static void add(List<ExecutionTracker.Ordering> held, String flowName, String what, String exchangeData,
                            ByteArrayToObjectConverter converter) {
        try {
            final ExchangeImpl exchange = converter.convert(BaseEncoding.base64().decode(exchangeData), ExchangeImpl.class);
            final ExecutionTracker tracker = ExecutionTracker.of(exchange);
            if (tracker != null && tracker.ordering() != null && flowName.equals(tracker.ordering().flowName())) {
                held.add(tracker.ordering());
            }
        } catch (RuntimeException unreadable) {
            SYS_LOGGER.warn("Flow '{}': the {} cannot be read, the order of its key is not kept against it: {}",
                flowName, what, unreadable.getMessage());
        }
    }
}
