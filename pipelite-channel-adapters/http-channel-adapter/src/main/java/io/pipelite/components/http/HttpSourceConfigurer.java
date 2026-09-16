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
package io.pipelite.components.http;

import io.pipelite.dsl.definition.SourceConfigurer;

import java.util.Map;

/**
 * Restricts which single HTTP method a given registered resource accepts - e.g. {@code
 * .fromSource("http://orders", (HttpSourceConfigurer c) -> c.method("POST"))}. Unset (the
 * default), any method is accepted, exactly as before this existed. Deliberately per-source, not
 * adapter-wide: the listening port (shared across every HTTP endpoint in the same process) belongs
 * on a future {@code HttpChannelConfigurer}/{@code HttpChannelConfiguration} instead, mirroring
 * how Kafka splits {@code bootstrapServers} (adapter-wide) from {@code groupId} (per-source).
 * <p>
 * Replaces {@code DefaultHttpHandler}'s previous {@code ALLOWED_REQUEST_METHODS} constant, which
 * was declared but never actually consulted anywhere (issue #62) - a single hardcoded
 * {@code [POST, PUT]} allow-list shared by every resource, not a real per-endpoint restriction.
 */
public final class HttpSourceConfigurer extends SourceConfigurer {

    private String method;

    public HttpSourceConfigurer method(String method) {
        this.method = method;
        return this;
    }

    @Override
    protected void contributeQueryParameters(Map<String, String> parameters) {
        if (method != null) {
            parameters.put(HttpConstants.METHOD_PROPERTY_NAME, method);
        }
    }

}
