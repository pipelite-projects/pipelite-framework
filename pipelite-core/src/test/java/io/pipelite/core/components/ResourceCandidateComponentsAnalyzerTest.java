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

import io.pipelite.components.link.LinkChannelAdapter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;

public class ResourceCandidateComponentsAnalyzerTest {

    private ResourceCandidateComponentsAnalyzer subject;

    @Before
    public void setup() {
        subject = new ResourceCandidateComponentsAnalyzer();
    }

    @Test
    public void shouldResolveCandidates() throws MalformedURLException {

        final ClassLoader cl = getClass().getClassLoader();
        final URL resourceURL = cl.getResource("META-INF/pipelite.factories");
        final Collection<CandidateComponentMetadata> candidates = subject.apply(resourceURL);
        Assert.assertNotNull(candidates);

        final Optional<CandidateComponentMetadata> candidateHolder = candidates.stream()
            .filter(ccm -> "link".equals(ccm.getProtocolName()))
            .findFirst();

        Assert.assertTrue(candidateHolder.isPresent());
        final CandidateComponentMetadata actual = candidateHolder.get();
        Assert.assertEquals(actual.getChannelAdapterType(), LinkChannelAdapter.class);

    }
}
