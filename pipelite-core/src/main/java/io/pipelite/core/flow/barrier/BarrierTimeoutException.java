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

import java.time.Duration;
import java.util.Set;

/**
 * An exchange waited at a barrier for longer than the barrier allows: it is given up, and goes to the dead letter
 * queue with this exception, which says what it was still waiting for.
 */
public class BarrierTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String barrierId;
    private final Set<Pending> pending;

    public BarrierTimeoutException(String barrierId, Set<Pending> pending, Duration timeout) {
        super(String.format("Barrier '%s' gave up after %s: still waiting for %s", barrierId, timeout, pending));
        this.barrierId = barrierId;
        this.pending = Set.copyOf(pending);
    }

    public String getBarrierId() {
        return barrierId;
    }

    /** The flows, with the key, that had not completed. */
    public Set<Pending> getPending() {
        return pending;
    }
}
