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

import io.pipelite.spi.channel.ChannelURL;
import org.junit.Assert;
import org.junit.Test;

/**
 * Issue #102: a destination is a URL. The rule is the same for a value written in the DSL and for
 * one about to be delivered to, except that a value only known at runtime cannot be checked earlier.
 */
public class DestinationURLsTest {

    @Test
    public void givenAURL_whenWrittenInTheDSL_thenItIsAccepted() {
        Assert.assertEquals("queue://x", DestinationURLs.requireStatic("queue://x", "toChannel(...)"));
        Assert.assertEquals("kafka://orders", DestinationURLs.requireStatic("kafka://orders", "wireTap(...)"));
    }

    @Test
    public void givenABareName_whenWrittenInTheDSL_thenItIsRejectedNamingTheConstructAndWhatToWrite() {
        try {
            DestinationURLs.requireStatic("kitchen-start", "then(...)");
            Assert.fail("expected the bare name to be rejected");
        } catch (IllegalArgumentException expected) {
            final String message = expected.getMessage();
            Assert.assertTrue(message, message.contains("then(...)"));
            Assert.assertTrue(message, message.contains("'kitchen-start' is not a URL"));
            Assert.assertTrue(message, message.contains("queue://kitchen-start"));
        }
    }

    @Test
    public void givenAnExpression_whenWrittenInTheDSL_thenItIsLeftForRuntime() {
        // Only known when an exchange is routed: supplyExchange checks the evaluated value then.
        final String dynamic = "#{Headers['destination']}";
        Assert.assertEquals(dynamic, DestinationURLs.requireStatic(dynamic, "otherwise(...)"));
        Assert.assertNull(DestinationURLs.requireStatic(null, "otherwise(...)"));
    }

    @Test
    public void givenAURL_whenAboutToBeDeliveredTo_thenItIsParsed() {
        final ChannelURL url = DestinationURLs.require("queue://kitchen-start");
        Assert.assertEquals("queue", url.getProtocol());
        Assert.assertEquals("kitchen-start", url.getEndpointURL());
    }

    @Test
    public void givenABareName_whenAboutToBeDeliveredTo_thenItIsRejectedSayingWhatToWrite() {
        try {
            DestinationURLs.require("kitchen-start");
            Assert.fail("expected the bare name to be rejected");
        } catch (IllegalArgumentException expected) {
            final String message = expected.getMessage();
            Assert.assertTrue(message, message.contains("'kitchen-start' is not a URL"));
            Assert.assertTrue(message, message.contains("queue://kitchen-start"));
        }
    }

    @Test
    public void givenNothing_whenAboutToBeDeliveredTo_thenItIsRejected() {
        for (String value : new String[]{null, ""}) {
            try {
                DestinationURLs.require(value);
                Assert.fail("expected " + value + " to be rejected");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

}
