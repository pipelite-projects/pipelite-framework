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

import org.junit.Assert;
import org.junit.Test;

/**
 * Issue #111: a source is a URL, always with a protocol. What used to make a source internal, the
 * lack of one, is now the mistake.
 */
public class SourceURLsTest {

    @Test
    public void givenABareName_whenWrittenInTheDSL_thenItIsRejectedSuggestingTheQueue() {
        try {
            SourceURLs.requireStatic("orders");
            Assert.fail("expected a bare name not to be a source");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals(
                "fromSource(\"orders\"): 'orders' is not a URL - a source always has a protocol. Write 'queue://orders' " +
                    "for the queue in front of an internal flow, or the protocol of the channel adapter that feeds it (http://, kafka://, ...)",
                expected.getMessage());
        }
    }

    @Test
    public void givenABareNameWithParameters_thenTheSuggestionKeepsThem() {
        try {
            SourceURLs.requireStatic("orders?concurrency=2");
            Assert.fail("expected a bare name not to be a source");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Write 'queue://orders?concurrency=2'"));
        }
    }

    @Test
    public void givenAURL_thenItIsReturnedUnchanged() {
        Assert.assertEquals("queue://orders", SourceURLs.requireStatic("queue://orders"));
        Assert.assertEquals("kafka://orders", SourceURLs.requireStatic("kafka://orders"));
        Assert.assertEquals("time://tick?period=1000", SourceURLs.requireStatic("time://tick?period=1000"));
    }

    @Test
    public void givenAValueWithAPlaceholderOrNothing_whenWrittenInTheDSL_thenItIsLeftToTheResolution() {
        Assert.assertEquals("${orders.source}", SourceURLs.requireStatic("${orders.source}"));
        Assert.assertEquals("queue://${orders.name}", SourceURLs.requireStatic("queue://${orders.name}"));
        Assert.assertNull(SourceURLs.requireStatic(null));
    }

    @Test
    public void givenAResolvedValueWithoutAProtocol_thenItIsRejected() {
        try {
            SourceURLs.requireProtocol("orders");
            Assert.fail("expected a bare name not to be a source");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Write 'queue://orders'"));
        }
        SourceURLs.requireProtocol("queue://orders");
    }

}
