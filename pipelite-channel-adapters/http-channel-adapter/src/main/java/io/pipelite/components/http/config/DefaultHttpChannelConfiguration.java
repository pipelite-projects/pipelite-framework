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

import io.pipelite.common.support.Preconditions;

public class DefaultHttpChannelConfiguration implements HttpChannelConfiguration {

    /**
     * Was hardcoded to 80 before this existed - on Linux, binding it requires the whole process
     * to run with elevated privileges just for the HTTP listener (issue #52). 8080 needs none,
     * the same default Spring Boot itself uses.
     */
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "0.0.0.0";

    /**
     * Generous but finite, same philosophy as this codebase's other unconfigured-default caps
     * (e.g. {@code FlowExecutionDumpInMemoryRepository#DEFAULT_MAX_SIZE}) - before this existed, a
     * single request's body was read into memory with no bound at all (issue #52).
     */
    public static final long DEFAULT_MAX_ENTITY_SIZE = 10L * 1024 * 1024;

    private Integer port;
    private String host;
    private Long maxEntitySize;

    @Override
    public void setPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }
        this.port = port;
    }

    @Override
    public int getPort() {
        return port != null ? port : DEFAULT_PORT;
    }

    @Override
    public void setHost(String host) {
        Preconditions.hasText(host, "host is required and cannot be blank");
        this.host = host;
    }

    @Override
    public String getHost() {
        return host != null ? host : DEFAULT_HOST;
    }

    @Override
    public void setMaxEntitySize(long maxEntitySize) {
        if (maxEntitySize < 1) {
            throw new IllegalArgumentException("maxEntitySize must be positive, got " + maxEntitySize);
        }
        this.maxEntitySize = maxEntitySize;
    }

    @Override
    public long getMaxEntitySize() {
        return maxEntitySize != null ? maxEntitySize : DEFAULT_MAX_ENTITY_SIZE;
    }

}
