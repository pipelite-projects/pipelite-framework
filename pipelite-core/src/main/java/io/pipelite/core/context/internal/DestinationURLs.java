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
 * flow is addressed as {@code link://<source endpoint name>}, an external system through its
 * channel adapter's protocol. A bare name is never a destination - it would be ambiguous between a
 * source endpoint name and a flow name, two different identities.
 * <p>
 * Applied at two points that must agree: when the DSL builds a flow ({@link #requireStatic}, which
 * lets through a value that is only known at runtime), and when {@code
 * PipeliteContext#supplyExchange} is asked to deliver ({@link #require}, which also covers those
 * runtime values). Public only because both callers live in different packages of {@code
 * pipelite-core}: not part of the API.
 */
public final class DestinationURLs {

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
                "%s: '%s' is not a URL - flows are addressed by URL, write '%s' to reach the flow whose source is '%s'",
                declaredBy, destination, ChannelProtocols.linkURL(destination), destination));
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
                "Destination '%s' is not a URL, unable to supply exchange - write '%s' to reach the flow whose source is '%s'",
                destinationURL, ChannelProtocols.linkURL(destinationURL), destinationURL));
        }
        return channelURL;
    }

    private static boolean hasProtocol(String destination) {
        // ChannelURL.parse throws for an empty value or a protocol with nothing after it, which
        // is also a malformed destination: let that message through untouched.
        return ChannelURL.parse(destination).hasProtocol();
    }

}
