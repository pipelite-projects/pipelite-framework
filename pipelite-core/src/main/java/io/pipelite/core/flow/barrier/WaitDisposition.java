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
package io.pipelite.core.flow.barrier;

/**
 * What the barrier node does with an exchange that has just arrived, as the {@link BarrierRepository} decided
 * it when it registered the wait.
 */
public record WaitDisposition(Kind kind, Pending failedBy) {

    public enum Kind {
        /** Something is still missing: the node returns, and the worker is free. */
        HOLD,
        /** Everything it waits for had already completed: the node goes on, on its own thread. */
        PROCEED,
        /** A flow it waits for had already failed ({@code failedBy}): the error handling of the flow, at once. */
        FAIL,
        /** An exchange with this id was already released or failed: the recovery owns it, the node does nothing. */
        DISCARD
    }

    private static final WaitDisposition HOLD = new WaitDisposition(Kind.HOLD, null);
    private static final WaitDisposition PROCEED = new WaitDisposition(Kind.PROCEED, null);
    private static final WaitDisposition DISCARD = new WaitDisposition(Kind.DISCARD, null);

    public static WaitDisposition hold() {
        return HOLD;
    }

    public static WaitDisposition proceed() {
        return PROCEED;
    }

    public static WaitDisposition discard() {
        return DISCARD;
    }

    public static WaitDisposition fail(Pending failedBy) {
        return new WaitDisposition(Kind.FAIL, failedBy);
    }
}
