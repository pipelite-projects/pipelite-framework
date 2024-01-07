package io.pipelite.core.components;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Collection;

public class FactoryChannelAdapterScannerTestAdapter {

    private FactoryComponentClasspathScanner subject;

    @Before
    public void setup(){
        subject = new FactoryComponentClasspathScanner();
    }

    @Test
    public void shouldResolveResources(){
        final Collection<URL> resources = subject.scanResources();
        Assert.assertNotNull(resources);
    }
}
