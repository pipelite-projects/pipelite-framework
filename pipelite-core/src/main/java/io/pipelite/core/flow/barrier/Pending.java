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

import java.io.Serializable;
import java.util.Objects;

/** What a waiting exchange waits for: the completion of the execution of a flow, for a key. */
public record Pending(String flow, String key) implements Serializable {

    private static final long serialVersionUID = 1L;

    public Pending {
        Objects.requireNonNull(flow, "flow is required and cannot be null");
        Objects.requireNonNull(key, "key is required and cannot be null");
    }

    /** {@code flow} is the name of the queue the awaited flow reads. */
    @Override
    public String toString() {
        return "queue://" + flow + " for key '" + key + "'";
    }
}
