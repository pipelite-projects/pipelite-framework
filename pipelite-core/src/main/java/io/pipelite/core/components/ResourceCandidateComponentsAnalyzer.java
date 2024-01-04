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

import io.pipelite.spi.channel.ChannelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.function.Function;

public class ResourceCandidateComponentsAnalyzer implements Function<URL, Collection<CandidateComponentMetadata>> {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    @Override
    @SuppressWarnings("unchecked")
    public Collection<CandidateComponentMetadata> apply(URL resourceURL) {

        if (sysLogger.isTraceEnabled()) {
            sysLogger.trace("ooo Analyzing resource {}", resourceURL);
        }

        final Collection<CandidateComponentMetadata> candidates = new ArrayList<>();
        try {
            final Properties componentProps = new Properties();
            componentProps.load(resourceURL.openStream());
            componentProps.forEach((key, fqdn) -> {
                final String protocolName = String.valueOf(key);
                final String className = String.valueOf(fqdn);
                try {
                    final Class<?> loadedClass = Class.forName(className);
                    if (ChannelAdapter.class.isAssignableFrom(loadedClass)) {
                        candidates.add(new CandidateComponentMetadata(protocolName, (Class<? extends ChannelAdapter>) loadedClass));
                        if (sysLogger.isDebugEnabled()) {
                            sysLogger.debug("=== Candidate component found, [{}] {}", protocolName, className);
                        }
                    }
                } catch (ClassNotFoundException exception) {
                    if (sysLogger.isWarnEnabled()) {
                        sysLogger.warn("Unable to load component class '{}'", className, exception);
                    }
                }
            });
        } catch (IOException ioException) {
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("Unable to load resource '{}'", resourceURL, ioException);
            }
        }
        return candidates;
    }

}
