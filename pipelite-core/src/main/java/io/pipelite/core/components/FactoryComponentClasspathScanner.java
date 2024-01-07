package io.pipelite.core.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;

public class FactoryComponentClasspathScanner {

    private static final String PIPELITE_FACTORIES_RESOURCE_NAME = "META-INF/pipelite.factories";

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    final ClassLoader classLoader = FactoryComponentClasspathScanner.class.getClassLoader();

    public Collection<URL> scanResources(){
        try{
            final Enumeration<URL> resourceURLs = classLoader.getResources(PIPELITE_FACTORIES_RESOURCE_NAME);
            return Collections.list(resourceURLs);
        } catch (IOException exception){
            if(sysLogger.isErrorEnabled()) {
                sysLogger.error("An underlying I/O error occurred scanning resources", exception);
            }
            return new ArrayList<>();
        }
    }

}

