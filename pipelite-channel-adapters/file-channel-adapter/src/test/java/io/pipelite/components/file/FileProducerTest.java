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

import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class FileProducerTest {

    private static final String RESOURCE_PATTERN = "[a-zA-Z0-9_./:@\\\\\\-]+";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldWriteStringPayloadToStaticPath() throws IOException {
        final Path target = temporaryFolder.getRoot().toPath().resolve("out.txt");
        final FileProducer subject = newProducer(target, false);

        subject.process(exchangeWithPayload("hello world"));

        assertEquals("hello world", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldWriteByteArrayPayload() throws IOException {
        final Path target = temporaryFolder.getRoot().toPath().resolve("out.bin");
        final FileProducer subject = newProducer(target, false);

        subject.process(exchangeWithPayload(new byte[]{1, 2, 3}));

        assertEquals(3, Files.size(target));
    }

    @Test
    public void shouldWriteInputStreamPayload() throws IOException {
        final Path target = temporaryFolder.getRoot().toPath().resolve("out-stream.txt");
        final FileProducer subject = newProducer(target, false);

        subject.process(exchangeWithPayload(new ByteArrayInputStream("streamed".getBytes(StandardCharsets.UTF_8))));

        assertEquals("streamed", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldTruncateExistingFileByDefault() throws IOException {
        final Path target = temporaryFolder.getRoot().toPath().resolve("out.txt");
        final FileProducer subject = newProducer(target, false);

        subject.process(exchangeWithPayload("first"));
        subject.process(exchangeWithPayload("second"));

        assertEquals("second", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldAppendWhenAppendPropertyIsTrue() throws IOException {
        final Path target = temporaryFolder.getRoot().toPath().resolve("out.txt");
        final FileProducer subject = newProducer(target, true);

        subject.process(exchangeWithPayload("first\n"));
        subject.process(exchangeWithPayload("second\n"));

        assertEquals("first\nsecond\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldCreateMissingParentDirectories() throws IOException {
        final Path target = temporaryFolder.getRoot().toPath().resolve("nested").resolve("dir").resolve("out.txt");
        final FileProducer subject = newProducer(target, false);

        subject.process(exchangeWithPayload("hello"));

        assertEquals("hello", Files.readString(target, StandardCharsets.UTF_8));
    }

    private static FileProducer newProducer(Path target, boolean append) {
        final String query = append ? "?append=true" : "";
        final EndpointURL endpointURL = EndpointURL.parse(target.toString() + query, RESOURCE_PATTERN);
        final Endpoint endpoint = new DefaultEndpoint(endpointURL);
        return new FileProducer(endpoint);
    }

    private static Exchange exchangeWithPayload(Object payload) {
        final Exchange exchange = new Exchange(new SimpleMessage("test-id"));
        exchange.setInputPayload(payload);
        return exchange;
    }

}
