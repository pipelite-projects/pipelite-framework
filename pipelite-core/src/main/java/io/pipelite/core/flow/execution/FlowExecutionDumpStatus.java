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
package io.pipelite.core.flow.execution;

/**
 * A {@link FlowExecutionDump}'s own claim state — lets {@link FlowExecutionDumpRepository#poll()}
 * skip a dump that's already being resumed, instead of handing it out again while a previous
 * resume attempt is still running. Carried on the dump itself (durable, part of what {@code save()}
 * persists) rather than passed around as an external exclusion list, so the same claim correctly
 * excludes a dump from every future {@code poll()} call regardless of which {@code
 * FlowExecutionDumpRepository} instance or process makes it — see {@link
 * FlowExecutionDumpRepository#tryClaim(String)}.
 */
public enum FlowExecutionDumpStatus {

    /**
     * Not currently claimed by anyone — eligible to be returned by {@link
     * FlowExecutionDumpRepository#poll()} and claimed via {@link
     * FlowExecutionDumpRepository#tryClaim(String)}. The only status a newly created dump ever
     * starts in.
     */
    PENDING,

    /**
     * Successfully claimed by some caller's {@link FlowExecutionDumpRepository#tryClaim(String)}
     * call — a resume attempt is (or, if that caller crashed mid-attempt, was) in progress for
     * this dump. Invisible to {@link FlowExecutionDumpRepository#poll()} while in this state.
     */
    IN_PROGRESS

}
