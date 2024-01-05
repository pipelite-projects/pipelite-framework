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

