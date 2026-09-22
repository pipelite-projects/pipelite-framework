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
package io.pipelite.core.components;

import io.pipelite.components.queue.QueueChannelAdapter;
import io.pipelite.components.slf4j.Slf4JChannelAdapter;
import io.pipelite.components.time.TimeChannelAdapter;
import io.pipelite.spi.channel.ChannelAdapter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * Issue #57: two different classes claiming the same protocol fail fast instead of one silently
 * overwriting the other. Exercised through {@link ChannelAdapterDiscovery#instantiate}, which
 * {@link #discover()} feeds from a real classpath scan - this needs only {@link
 * CandidateComponentMetadata} instances, constructed directly.
 */
public class ChannelAdapterDiscoveryTest {

    private ChannelAdapterDiscovery subject;

    @Before
    public void setup() {
        subject = new ChannelAdapterDiscovery();
    }

    @Test
    public void givenEveryProtocolHasOneClaimant_thenEachIsInstantiated() {
        final Map<String, ChannelAdapter> discovered = subject.instantiate(List.of(
            new CandidateComponentMetadata("queue", QueueChannelAdapter.class),
            new CandidateComponentMetadata("slf4j", Slf4JChannelAdapter.class)));

        Assert.assertEquals(2, discovered.size());
        Assert.assertTrue(discovered.get("queue") instanceof QueueChannelAdapter);
        Assert.assertTrue(discovered.get("slf4j") instanceof Slf4JChannelAdapter);
    }

    @Test
    public void givenTheSameProtocolAndClassAppearTwice_thenItIsNotACollision() {
        // The same factories entry reachable through more than one classpath resource URL, say -
        // not two adapters disagreeing about who owns the protocol.
        final Map<String, ChannelAdapter> discovered = subject.instantiate(List.of(
            new CandidateComponentMetadata("queue", QueueChannelAdapter.class),
            new CandidateComponentMetadata("queue", QueueChannelAdapter.class)));

        Assert.assertEquals(1, discovered.size());
    }

    @Test
    public void givenTwoDifferentClassesClaimTheSameProtocol_thenItFailsFast() {
        try {
            subject.instantiate(List.of(
                new CandidateComponentMetadata("queue", QueueChannelAdapter.class),
                new CandidateComponentMetadata("queue", TimeChannelAdapter.class)));
            Assert.fail("expected the collision to be rejected");
        } catch (DuplicateChannelAdapterProtocolException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("queue"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(QueueChannelAdapter.class.getName()));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(TimeChannelAdapter.class.getName()));
        }
    }

    @Test
    public void givenTheSameClassClaimsTwoDifferentProtocols_thenBothAreKept() {
        // Not a collision on either axis: the reverse of the usual case, but legitimate - nothing
        // says one adapter class can't answer for two schemes.
        final Map<String, ChannelAdapter> discovered = subject.instantiate(List.of(
            new CandidateComponentMetadata("queue", QueueChannelAdapter.class),
            new CandidateComponentMetadata("link", QueueChannelAdapter.class)));

        Assert.assertEquals(2, discovered.size());
    }

}
