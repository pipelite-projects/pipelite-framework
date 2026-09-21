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
package io.pipelite.spi.inbox;

import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

/**
 * Issue #108: the inbox belongs to a flow, identified by its name - not to the resource of its
 * source, which several flows can share.
 */
public class SegmentedLogDurableInboxProviderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void givenTheSameFlow_thenTheSameInboxIsReused() throws IOException {
        final SegmentedLogDurableInboxProvider provider = new SegmentedLogDurableInboxProvider(
            temporaryFolder.newFolder("inbox").toPath(), new DistributedIdentityGeneratorImpl());

        Assert.assertSame(provider.forFlow("kitchen-flow"), provider.forFlow("kitchen-flow"));
    }

    @Test
    public void givenTwoFlows_thenEachHasItsOwnInbox() throws IOException {
        final SegmentedLogDurableInboxProvider provider = new SegmentedLogDurableInboxProvider(
            temporaryFolder.newFolder("inbox").toPath(), new DistributedIdentityGeneratorImpl());

        final DurableInbox first = provider.forFlow("orders-http-flow");
        final DurableInbox second = provider.forFlow("orders-internal-flow");
        first.enqueue("for-first".getBytes(), java.util.Map.of());

        Assert.assertNotSame(first, second);
        Assert.assertEquals(1, first.pendingEntries().size());
        Assert.assertTrue(second.pendingEntries().isEmpty());
    }

}
