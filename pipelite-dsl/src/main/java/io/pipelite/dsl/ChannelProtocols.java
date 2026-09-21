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
package io.pipelite.dsl;

/**
 * Names of the channel adapter protocols the framework itself relies on, defined once so no caller
 * has to repeat the literal.
 */
public final class ChannelProtocols {

    /**
     * The protocol that addresses an internal flow: {@code link://x} delivers to the flow whose
     * source endpoint is declared as {@code fromSource("x")} (a plain resource name, with no
     * protocol). It is registered under this name by the {@code link} channel adapter in
     * {@code META-INF/pipelite.factories}, which cannot reference this constant, so a test keeps
     * the two aligned.
     */
    public static final String LINK = "link";

    private ChannelProtocols() {
    }

    /**
     * The URL that addresses the internal flow whose source endpoint is {@code sourceEndpointName}.
     */
    public static String linkURL(String sourceEndpointName) {
        return LINK + "://" + sourceEndpointName;
    }

}
