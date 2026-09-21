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
package io.pipelite.core.context;

/**
 * Thrown by {@link io.pipelite.core.context.impl.DefaultEndpointFactory#createEndpoint} when a
 * {@code fromSource}/{@code toSink} URL resolved through a channel adapter other than the queue one
 * declares {@code concurrency} and/or {@code executorType}. Those parameters
 * are only meaningful for a queue source ({@code queue://}, see {@code QueueChannelAdapter}, whose
 * consumers are the concurrent consumers of one flow) - any other channel adapter silently ignoring
 * them would let a developer believe concurrency is active when it never was.
 */
public class UnsupportedSourceConcurrencyException extends RuntimeException {

    public UnsupportedSourceConcurrencyException(String protocol, String parameterName) {
        super(String.format(
            "'%s' is not supported on a '%s://' endpoint: concurrency only applies to a fromSource(\"queue://...\"). " +
                "Remove it from the URL, or move the concurrent work to a second flow that reads a queue://.", parameterName, protocol));
    }

}
