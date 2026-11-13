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
package io.pipelite.core.exchange.internal;

import io.pipelite.core.context.internal.IOKeys;

import java.util.Map;

/**
 * What an exchange property is about, which decides whether another exchange made from it inherits it (issue #149).
 * The framework keeps several kinds of thing on an exchange, and each place that makes an exchange from another used
 * to remove by hand the ones that did not belong to it. They are classified here, once.
 * <ul>
 *     <li>{@link #INBOX}: the entry of the durable inbox the exchange was admitted with. The acknowledgement belongs
 *     to that exchange, so no other exchange made from it carries the entry.</li>
 *     <li>{@link #ATTEMPT}: the state of an attempt to run the exchange (the number of attempts, the dump, where it
 *     failed and why). An exchange of its own starts at zero.</li>
 *     <li>{@link #RUN}: the tracker of the execution the exchange is in. An exchange that continues the same execution,
 *     like the elements of a split, shares it; one that goes to another flow starts an execution of its own.</li>
 *     <li>{@link #EXCHANGE}: everything else, the return address, the routing slip, the idempotency key and the
 *     properties of the application, which belong to the work the exchange carries.</li>
 * </ul>
 */
public enum PropertyScope {

    INBOX,
    ATTEMPT,
    RUN,
    EXCHANGE;

    private static final Map<String, PropertyScope> KNOWN = Map.ofEntries(
        Map.entry(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, INBOX),
        Map.entry(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME, INBOX),
        Map.entry(IOKeys.FAILURE_EXCEPTION_TYPE_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FAILURE_EXCEPTION_MESSAGE_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FAILURE_STACK_TRACE_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, ATTEMPT),
        Map.entry(IOKeys.EXECUTION_TRACKER_PROPERTY_NAME, RUN));

    /** The scope of the property {@code propertyName}: {@link #EXCHANGE} unless the framework classified it. */
    public static PropertyScope of(String propertyName) {
        return KNOWN.getOrDefault(propertyName, EXCHANGE);
    }

    /** Whether an exchange that continues the same execution as the one it is made from inherits the property. */
    public boolean inheritedWithinAnExecution() {
        return this == EXCHANGE || this == RUN;
    }
}
