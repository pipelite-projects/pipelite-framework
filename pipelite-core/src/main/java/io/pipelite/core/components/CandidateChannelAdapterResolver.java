package io.pipelite.core.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Collection;
import java.util.stream.Collectors;

public class CandidateChannelAdapterResolver {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private static final ResourceCandidateComponentsAnalyzer RESOURCE_COMPONENT_RESOLVER = new ResourceCandidateComponentsAnalyzer();
    private final FactoryComponentClasspathScanner factoryScanner;

    public CandidateChannelAdapterResolver(){
        factoryScanner = new FactoryComponentClasspathScanner();
    }

    public Collection<CandidateComponentMetadata> findCandidates(){

        final Collection<URL> resourceURLs = factoryScanner.scanResources();
        return resourceURLs
            .stream()
            .map(RESOURCE_COMPONENT_RESOLVER)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

    }

}
