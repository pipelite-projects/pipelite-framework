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
package io.pipelite.core.context.internal;

import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.spi.channel.ChannelURL;

/**
 * The one rule for what a flow may read from (issue #111), the counterpart of {@link
 * DestinationURLs}: a source is a URL, always with a protocol. The queue in front of an internal
 * flow is {@code queue://<name>}, an external system is read through its channel adapter's
 * protocol. A bare name is never a source - it used to mean "an internal flow", so a protocol
 * forgotten on {@code fromSource("orders")} silently made a source that nothing feeds.
 * <p>
 * Applied at two points that must agree: when the DSL builds a flow ({@link #requireStatic}, which
 * lets through a value with a {@code ${...}} placeholder, only known once resolved), and when the
 * endpoint is created from the resolved URL ({@link #requireProtocol}). Public only because both
 * callers live in different packages of {@code pipelite-core}: not part of the API.
 */
public final class SourceURLs {

    private static final String PLACEHOLDER_START = "${";

    private SourceURLs() {
    }

    /**
     * For a source written in the DSL. A value with a placeholder is resolved, and checked, when
     * the endpoint is created.
     *
     * @return the source, unchanged
     * @throws IllegalArgumentException if the value is not a URL
     */
    public static String requireStatic(String source) {
        if (source == null || source.contains(PLACEHOLDER_START)) {
            return source;
        }
        requireProtocol(source);
        return source;
    }

    /**
     * For a source whose placeholders are already resolved.
     *
     * @throws IllegalArgumentException if the value is not a URL
     */
    public static void requireProtocol(String resolvedSource) {
        // ChannelURL.parse throws for an empty value or a protocol with nothing after it, which
        // is also a malformed source: let that message through untouched.
        if (!ChannelURL.parse(resolvedSource).hasProtocol()) {
            throw new IllegalArgumentException(String.format(
                "fromSource(\"%s\"): '%s' is not a URL - a source always has a protocol. Write '%s' for the queue " +
                    "in front of an internal flow, or the protocol of the channel adapter that feeds it (http://, kafka://, ...)",
                resolvedSource, resolvedSource, ChannelProtocols.queueURL(resolvedSource)));
        }
    }

}
