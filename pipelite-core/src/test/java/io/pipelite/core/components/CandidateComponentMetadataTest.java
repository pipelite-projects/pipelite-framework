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
import io.pipelite.components.time.TimeChannelAdapter;
import org.junit.Assert;
import org.junit.Test;

/**
 * Issue #57: {@code equals}/{@code hashCode} must key on both the protocol and the class - keying
 * on the class alone let a classpath scan's {@code Collectors.toSet()} silently collapse two
 * candidates that only happened to share a class, discarding whichever protocol the losing one
 * would have claimed.
 */
public class CandidateComponentMetadataTest {

    @Test
    public void givenTheSameProtocolAndClass_thenTheyAreEqual() {
        final CandidateComponentMetadata a = new CandidateComponentMetadata("queue", QueueChannelAdapter.class);
        final CandidateComponentMetadata b = new CandidateComponentMetadata("queue", QueueChannelAdapter.class);

        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void givenTheSameClassButADifferentProtocol_thenTheyAreNotEqual() {
        final CandidateComponentMetadata a = new CandidateComponentMetadata("queue", QueueChannelAdapter.class);
        final CandidateComponentMetadata b = new CandidateComponentMetadata("link", QueueChannelAdapter.class);

        Assert.assertNotEquals("the same class under two protocols is two distinct candidates, not a duplicate", a, b);
    }

    @Test
    public void givenTheSameProtocolButADifferentClass_thenTheyAreNotEqual() {
        final CandidateComponentMetadata a = new CandidateComponentMetadata("queue", QueueChannelAdapter.class);
        final CandidateComponentMetadata b = new CandidateComponentMetadata("queue", TimeChannelAdapter.class);

        Assert.assertNotEquals(a, b);
    }

}
