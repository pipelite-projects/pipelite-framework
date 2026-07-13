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
package io.pipelite.core.config;

/**
 * Resolves {@code ${key}} placeholder tokens found in a raw endpoint URL, before it is
 * parsed by {@code ChannelURL}/{@code EndpointURL}. Invoked unconditionally, eagerly, once
 * per endpoint at {@code PipeliteContext.start()} — never per-exchange.
 *
 * <p>The no-op implementation ({@link NoOpEndpointURLPropertyResolver}) is the default for
 * plain-Java/standalone use of {@code pipelite-core}; a Spring-backed implementation
 * (provided by {@code pipelite-spring-starter}) delegates to
 * {@code org.springframework.core.env.Environment}.
 */
public interface EndpointURLPropertyResolver {

    /**
     * Substitutes any {@code ${key}} tokens present in {@code rawUrl}. A no-op implementation
     * returns {@code rawUrl} unchanged; a Spring-backed implementation may throw at runtime if
     * a key cannot be resolved.
     */
    String resolve(String rawUrl);

}
