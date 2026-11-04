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
 * A flow that a barrier waits for failed definitively for the key of the waiting exchange: the exchange does not
 * wait for the others, and goes to the error handling of its flow with this exception.
 */
public class BarrierFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String barrierId;
    private final Pending failedBy;

    public BarrierFailedException(String barrierId, Pending failedBy) {
        super(String.format("Barrier '%s' failed: the flow that reads %s failed", barrierId, failedBy));
        this.barrierId = barrierId;
        this.failedBy = failedBy;
    }

    public String getBarrierId() {
        return barrierId;
    }

    /** The awaited flow and the key that failed. */
    public Pending getFailedBy() {
        return failedBy;
    }
}
