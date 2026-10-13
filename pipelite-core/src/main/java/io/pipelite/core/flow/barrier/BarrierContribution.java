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

import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.HeadersImpl;

import java.io.Serializable;
import java.util.Objects;

/**
 * What one contributor handed to a barrier: the headers and the payload it arrived with, and the id of its
 * message. The properties of the exchange are deliberately not kept: they belong to the thread that is
 * processing it (the inbox entry, the retry dump), not to what the contributor said. A contribution is the
 * same whatever way it arrived at the barrier.
 */
public final class BarrierContribution implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String contributorId;
    private final String messageId;
    private final HeadersImpl headers;
    private final Object payload;

    public BarrierContribution(String contributorId, String messageId, HeadersImpl headers, Object payload) {
        this.contributorId = Objects.requireNonNull(contributorId, "contributorId is required and cannot be null");
        this.messageId = messageId;
        this.headers = Objects.requireNonNull(headers, "headers is required and cannot be null");
        this.payload = payload;
    }

    static BarrierContribution of(String contributorId, ExchangeImpl exchange) {
        return new BarrierContribution(contributorId, exchange.getInput().getId(),
            new HeadersImpl((HeadersImpl) exchange.getHeaders()), exchange.getInputPayload());
    }

    public String getContributorId() {
        return contributorId;
    }

    public String getMessageId() {
        return messageId;
    }

    /** A copy: the contribution keeps its own headers. */
    public HeadersImpl getHeaders() {
        return new HeadersImpl(headers);
    }

    public Object getPayload() {
        return payload;
    }
}
