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
package io.pipelite.core.context.internal;

public class IOKeys {

    // What the framework keeps on an exchange for its own use. They are exchange properties, not headers:
    // a header belongs to the user (a processor reads, writes and removes any of them, a route tests
    // them in an expression), and none of these is for user code to read or to be able to break.
    // Issues #152 and #159: the return address and the failure information used to be headers.
    public static final String RETURN_ADDRESS_PROPERTY_NAME = "X-Return-Address";
    public static final String ROUTING_SLIP_PROPERTY_NAME = "X-Routing-Slip";

    public static final String FAILURE_EXCEPTION_TYPE_PROPERTY_NAME = "X-Failure-Exception-Type";
    public static final String FAILURE_EXCEPTION_MESSAGE_PROPERTY_NAME = "X-Failure-Exception-Message";
    public static final String FAILURE_STACK_TRACE_PROPERTY_NAME = "X-Failure-Stack-Trace";

    public static final String FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME = "X-FlowExecution-Dump-Id";
    public static final String FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME = "X-FlowExecution-Last-Executed-Flow";
    public static final String FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME = "X-FlowExecution-Last-Executed-Flow-Source-Endpoint-Resource";
    public static final String FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME = "X-FlowExecution-Last-Executed-Processor";
    public static final String FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME = "X-FlowExecution-Attempt-Number";

    /**
     * Distinct from {@link #FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME}, which is
     * only ever written on success (by a post-processor) and therefore names the step
     * <em>before</em> the one that actually failed. This one is set once, at the exact catch
     * site inside {@code AbstractProcessorNode.process()}, and names the processor whose
     * {@code delegate.process(...)} call actually threw.
     */
    public static final String FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME = "X-FlowExecution-Failed-Processor";

    /**
     * Set on an {@code Exchange} by the durable-inbox write-through hook in {@code
     * EventDrivenConsumer#process}/{@code DefaultPollingConsumer#consume} (issue #70) once {@code
     * DurableInbox#enqueue} returns a non-null id — absent when durability is disabled for that
     * source ({@code NoOpDurableInbox}). Read back by the acknowledge hook in {@code
     * dispatchToNext}/{@code process} and by {@code RetryChannelExceptionHandler} at the moment a
     * failure is handed off to a retry channel, so both know which inbox entry to acknowledge.
     */
    public static final String DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME = "X-DurableInbox-Entry-Id";

    /**
     * Set by {@code DefaultPipeliteContext#recoverPendingInboxEntries} on a freshly-deserialized
     * {@code Exchange} before resupplying it through the same {@code consume()}/{@code process()}
     * path a brand-new message would take, naming the ALREADY-durable entry id it must resume as
     * rather than duplicate. Deliberately a distinct property from {@link
     * #DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME} above: that one is also (over)written whenever this
     * same shared {@code Exchange} instance is forwarded uncopied into a completely different
     * flow's own consumer (e.g. a plain {@code .toSink("queue://...")}), which legitimately DOES
     * need its own fresh entry in ITS OWN inbox — conflating the two would make that destination
     * silently skip its own durability instead. The write-through hook consumes this property
     * (copying its value into {@link #DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME} instead of enqueueing
     * a new entry) and removes it immediately, so it can never leak into a later, unrelated
     * forward of the same Exchange to a different flow.
     */
    public static final String DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME = "X-DurableInbox-Resupplied-Entry-Id";

    /**
     * The {@code ExecutionTracker} of an execution of a flow that a barrier awaits (issue #150): what to tell the
     * barriers when the execution ends, and how it went. Set when the execution starts, only for a flow some
     * barrier awaits; it travels with the exchange (a retry carries it in its dump) and is dropped when the
     * exchange is handed to the consumer of another flow, which has an execution of its own.
     */
    public static final String EXECUTION_TRACKER_PROPERTY_NAME = "X-Barrier-Execution-Tracker";

    private IOKeys(){
    }
}
