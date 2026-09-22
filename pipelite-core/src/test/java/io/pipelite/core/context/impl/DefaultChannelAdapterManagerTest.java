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
package io.pipelite.core.context.impl;

import io.pipelite.components.queue.QueueChannelAdapter;
import io.pipelite.components.time.TimeChannelAdapter;
import io.pipelite.core.components.DuplicateChannelAdapterProtocolException;
import io.pipelite.core.context.ChannelAdapterManager;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Issue #57: registering a second {@code ChannelAdapter} under a protocol another one already
 * holds fails fast, the same as a collision found while scanning the classpath
 * ({@code ChannelAdapterDiscoveryTest}) - {@code putIfAbsent} used to make this a silent no-op.
 */
public class DefaultChannelAdapterManagerTest {

    private ChannelAdapterManager subject;

    @Before
    public void setup() {
        subject = new DefaultChannelAdapterManager(new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl())));
    }

    @Test
    public void shouldRegisterAChannelAdapterUnderAFreeName() {
        subject.registerChannelAdapter("queue", new QueueChannelAdapter());
        Assert.assertTrue(subject.tryResolveChannel("queue").isPresent());
    }

    @Test
    public void givenTheNameIsAlreadyTaken_thenItFailsFastNamingBothClasses() {
        subject.registerChannelAdapter("queue", new QueueChannelAdapter());

        try {
            subject.registerChannelAdapter("queue", new TimeChannelAdapter());
            Assert.fail("expected the second registration to be rejected");
        } catch (DuplicateChannelAdapterProtocolException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("queue"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(QueueChannelAdapter.class.getName()));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(TimeChannelAdapter.class.getName()));
        }

        // The first registration must survive the rejected second one.
        Assert.assertTrue(subject.tryResolveChannel("queue").get() instanceof QueueChannelAdapter);
    }

}
