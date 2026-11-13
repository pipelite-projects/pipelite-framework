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

import io.pipelite.dsl.Headers;

public interface ExchangeFactory {

    ExchangeImpl createExchange();
    ExchangeImpl createExchange(Headers headers);
    ExchangeImpl createExchange(Headers headers, Object inputPayload);
    ExchangeImpl createExchange(Object inputPayload);
    ExchangeImpl copyExchange(ExchangeImpl exchange);
    ExchangeImpl nextExchange(ExchangeImpl current);

    /**
     * Makes an exchange with {@code payload} that continues the execution {@code parent} is in, like the elements of a
     * split (issue #149): it has the headers of {@code parent} and the properties that {@link PropertyScope} says an
     * exchange inherits within an execution, and a message of its own. It does not carry the inbox entry of
     * {@code parent} (the acknowledgement is the parent's) nor the state of its attempts.
     */
    ExchangeImpl deriveExchange(ExchangeImpl parent, Object payload);

}
