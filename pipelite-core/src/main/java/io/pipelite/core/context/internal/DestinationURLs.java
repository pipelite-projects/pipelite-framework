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

import io.pipelite.core.support.expression.ExpressionUtils;
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.spi.channel.ChannelURL;

/**
 * The one rule for where an exchange may be sent (issue #102): a destination is a URL. An internal
 * flow is addressed by the queue it reads, {@code queue://<queue name>}, an external system through
 * its channel adapter's protocol. A bare name is never a destination - it would be ambiguous
 * between a queue name and a flow name, two different identities (and, since issue #111, a source
 * is a URL too: {@link SourceURLs}).
 * <p>
 * Applied at two points that must agree: when the DSL builds a flow ({@link #requireStatic}, which
 * lets through a value that is only known at runtime), and when {@code
 * PipeliteContext#supplyExchange} is asked to deliver ({@link #require}, which also covers those
 * runtime values). Public only because both callers live in different packages of {@code
 * pipelite-core}: not part of the API.
 */
public final class DestinationURLs {

    private static final String PLACEHOLDER_START = "${";

    private DestinationURLs() {
    }

    /**
     * For a destination written in the DSL. A value that contains an expression ({@code #{...}}) is
     * only known at runtime and is checked then, by {@link #require}.
     *
     * @param declaredBy the DSL construct the value was written in, e.g. {@code toChannel(...)}
     * @return the destination, unchanged
     * @throws IllegalArgumentException if the value is not a URL
     */
    public static String requireStatic(String destination, String declaredBy) {
        if (destination == null || ExpressionUtils.hasExpressionText(destination)) {
            return destination;
        }
        if (!hasProtocol(destination)) {
            throw new IllegalArgumentException(String.format(
                "%s: '%s' is not a URL - flows are addressed by URL, write '%s' to reach the flow that reads the queue '%s'",
                declaredBy, destination, ChannelProtocols.queueURL(destination), destination));
        }
        return destination;
    }

    /**
     * For a destination about to be delivered to.
     *
     * @throws IllegalArgumentException if the value is not a URL
     */
    public static ChannelURL require(String destinationURL) {
        if (destinationURL == null || destinationURL.isEmpty()) {
            throw new IllegalArgumentException("Destination is required and cannot be null or empty, unable to supply exchange");
        }
        final ChannelURL channelURL = ChannelURL.parse(destinationURL);
        if (!channelURL.hasProtocol()) {
            throw new IllegalArgumentException(String.format(
                "Destination '%s' is not a URL, unable to supply exchange - write '%s' to reach the flow that reads the queue '%s'",
                destinationURL, ChannelProtocols.queueURL(destinationURL), destinationURL));
        }
        return channelURL;
    }

    /**
     * For the URL of a {@code toSink(...)} written in the DSL (issue #112). A sink always has a
     * protocol: without one it used to be a producer that delivered nowhere, the same as no sink at
     * all. A value with a {@code ${...}} placeholder is resolved, and checked, when the endpoint is
     * created ({@link #requireSinkProtocol}).
     *
     * @return the sink, unchanged
     * @throws IllegalArgumentException if the value is not a URL
     */
    public static String requireSink(String sink) {
        if (sink == null || sink.contains(PLACEHOLDER_START)) {
            return sink;
        }
        requireSinkProtocol(sink);
        return sink;
    }

    /**
     * For a sink whose placeholders are already resolved.
     *
     * @throws IllegalArgumentException if the value is not a URL
     */
    public static void requireSinkProtocol(String resolvedSink) {
        if (!hasProtocol(resolvedSink)) {
            throw new IllegalArgumentException(String.format(
                "toSink(\"%s\"): '%s' is not a URL - a sink always has a protocol. Write the URL of a channel adapter " +
                    "(kafka://, http://, slf4j://, ...), '%s' to hand the exchange to the flow that reads the queue '%s', " +
                    "or leave toSink(...) out to end the flow",
                resolvedSink, resolvedSink, ChannelProtocols.queueURL(resolvedSink), resolvedSink));
        }
    }

    private static boolean hasProtocol(String destination) {
        // ChannelURL.parse throws for an empty value or a protocol with nothing after it, which
        // is also a malformed destination: let that message through untouched.
        return ChannelURL.parse(destination).hasProtocol();
    }

}
