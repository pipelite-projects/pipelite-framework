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
 * Default {@link EndpointURLPropertyResolver} for plain-Java/standalone use of
 * {@code pipelite-core}: returns {@code rawUrl} unchanged. A literal {@code ${key}} left
 * in the URL therefore fails downstream, in {@code ChannelURL}/{@code EndpointURL} parsing,
 * with a generic malformed-url error rather than a dedicated placeholder exception.
 */
public class NoOpEndpointURLPropertyResolver implements EndpointURLPropertyResolver {

    @Override
    public String resolve(String rawUrl) {
        return rawUrl;
    }

}
