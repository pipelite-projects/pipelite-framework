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
package io.pipelite.components.http.config;

/**
 * Adapter-wide HTTP server settings (issues #48, #52) - one Undertow instance is shared by every
 * {@code http://} source in a context (see {@code HttpChannelAdapter}), so these belong here, not
 * on {@code HttpSourceConfigurer}, the same way Kafka splits {@code bootstrapServers} (adapter-wide)
 * from {@code groupId} (per-source).
 */
public interface HttpChannelConfiguration {

    void setPort(int port);
    int getPort();

    void setHost(String host);
    String getHost();

    /**
     * The upper bound on a single request's body size, in bytes - wired to Undertow's own {@code
     * UndertowOptions#MAX_ENTITY_SIZE} (issue #52). Before this existed, {@code
     * DefaultHttpHandler} read an incoming request body into a {@code String} with no limit at
     * all, a heap-exhaustion DoS vector on a single oversized request.
     */
    void setMaxEntitySize(long maxEntitySize);
    long getMaxEntitySize();

}
