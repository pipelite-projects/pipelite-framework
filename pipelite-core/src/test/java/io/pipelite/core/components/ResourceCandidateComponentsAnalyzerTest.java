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
