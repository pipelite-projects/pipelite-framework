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
