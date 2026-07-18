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
package io.pipelite.components.file;

import io.pipelite.spi.endpoint.DefaultProducer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointProperties;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileProducer extends DefaultProducer {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Path target;
    private final Charset charset;
    private final boolean append;

    public FileProducer(Endpoint endpoint) {
        super(endpoint);

        final EndpointURL endpointURL = endpoint.getEndpointURL();
        final EndpointProperties properties = endpointURL.getProperties();

        this.target = Path.of(endpointURL.getResource());
        this.charset = Charset.forName(properties.getOrDefault(FileConstants.CHARSET_PROPERTY_NAME, FileConstants.DEFAULT_CHARSET));
        this.append = Boolean.parseBoolean(properties.getOrDefault(FileConstants.APPEND_PROPERTY_NAME, "false"));
    }

    @Override
    public void process(Exchange exchange) {

        final StandardOpenOption[] options = append
            ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
            : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};

        try {
            final Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            final Object payload = exchange.getInputPayload();
            if (payload instanceof byte[]) {
                Files.write(target, (byte[]) payload, options);
            } else if (payload instanceof InputStream) {
                try (InputStream in = (InputStream) payload) {
                    Files.write(target, in.readAllBytes(), options);
                }
            } else if (payload instanceof CharSequence) {
                Files.writeString(target, (CharSequence) payload, charset, options);
            } else {
                sysLogger.warn("Payload of type '{}' is not natively supported by FileProducer; converting via toString()",
                    payload != null ? payload.getClass() : null);
                Files.writeString(target, String.valueOf(payload), charset, options);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to write to file '%s'", target), exception);
        }
    }

}
