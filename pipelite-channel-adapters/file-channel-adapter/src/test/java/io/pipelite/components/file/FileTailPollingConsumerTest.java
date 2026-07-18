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

import io.pipelite.dsl.Headers;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FileTailPollingConsumerTest {

    private static final String RESOURCE_PATTERN = "[a-zA-Z0-9_./:@\\\\\\-]+";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path stateDirectory;
    private DefaultFileChannelConfiguration configuration;

    @Before
    public void setup() {
        stateDirectory = temporaryFolder.getRoot().toPath().resolve("state");
        configuration = new DefaultFileChannelConfiguration();
        configuration.setStateDirectory(stateDirectory);
    }

    @Test
    public void shouldSkipExistingContentByDefaultOnFirstPoll() throws IOException {
        final Path file = temporaryFolder.getRoot().toPath().resolve("app.log");
        Files.writeString(file, "already-here\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer subject = newConsumer(file, "");

        final Exchange exchange = subject.receive(0);
        Assert.assertNull(exchange);
    }

    @Test
    public void shouldReadFromBeginningWhenStartPositionIsBeginning() throws IOException {
        final Path file = temporaryFolder.getRoot().toPath().resolve("app.log");
        Files.writeString(file, "first\nsecond\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer subject = newConsumer(file, "?startPosition=beginning");

        final Exchange first = subject.receive(0);
        Assert.assertNotNull(first);
        Assert.assertEquals("first", first.getInputPayload());
        Assert.assertEquals("app.log", first.getHeaders().tryGetHeader(FileConstants.FILE_NAME_EXCHANGE_HEADER_NAME).orElse(null));
        Assert.assertEquals(file.toAbsolutePath().toString(), first.getHeaders().tryGetHeader(FileConstants.FILE_PATH_EXCHANGE_HEADER_NAME).orElse(null));

        final Exchange second = subject.receive(0);
        Assert.assertNotNull(second);
        Assert.assertEquals("second", second.getInputPayload());

        Assert.assertNull(subject.receive(0));
    }

    @Test
    public void shouldReturnNewlyAppendedLinesOnSubsequentPolls() throws IOException {
        final Path file = temporaryFolder.getRoot().toPath().resolve("app.log");
        Files.writeString(file, "old\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer subject = newConsumer(file, "");
        Assert.assertNull(subject.receive(0)); // skips pre-existing content (startPosition=end default)

        Files.writeString(file, "new-line\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        final Exchange exchange = subject.receive(0);
        Assert.assertNotNull(exchange);
        Assert.assertEquals("new-line", exchange.getInputPayload());
    }

    @Test
    public void shouldReturnNullWhenFileDoesNotExistYet() {
        final Path file = temporaryFolder.getRoot().toPath().resolve("missing.log");
        final FileTailPollingConsumer subject = newConsumer(file, "?startPosition=beginning");

        Assert.assertNull(subject.receive(0));
    }

    @Test
    public void shouldResetOffsetWhenFileShrinks() throws IOException {
        final Path file = temporaryFolder.getRoot().toPath().resolve("app.log");
        Files.writeString(file, "aaaaaaaaaa\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer subject = newConsumer(file, "?startPosition=beginning");
        final Exchange first = subject.receive(0);
        Assert.assertEquals("aaaaaaaaaa", first.getInputPayload());

        // Truncate the file to simulate rotation/shrink, then write a fresh shorter line.
        Files.writeString(file, "b\n", StandardCharsets.UTF_8);

        final Exchange afterShrink = subject.receive(0);
        Assert.assertNotNull(afterShrink);
        Assert.assertEquals("b", afterShrink.getInputPayload());
    }

    @Test
    public void shouldPersistOffsetAcrossConsumerInstances() throws IOException {
        final Path file = temporaryFolder.getRoot().toPath().resolve("app.log");
        Files.writeString(file, "first\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer first = newConsumer(file, "?startPosition=beginning");
        Assert.assertEquals("first", first.receive(0).getInputPayload());

        Files.writeString(file, "second\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        // A brand new consumer instance (simulating a restart) must resume from the persisted offset,
        // not re-read "first" nor re-apply startPosition semantics.
        final FileTailPollingConsumer afterRestart = newConsumer(file, "?startPosition=beginning");
        final Exchange exchange = afterRestart.receive(0);
        Assert.assertNotNull(exchange);
        Assert.assertEquals("second", exchange.getInputPayload());
    }

    private FileTailPollingConsumer newConsumer(Path file, String query) {
        final EndpointURL endpointURL = EndpointURL.parse(file.toString() + query, RESOURCE_PATTERN);
        final Endpoint endpoint = new DefaultEndpoint(endpointURL);
        final FileTailPollingConsumer consumer = new FileTailPollingConsumer(endpoint, configuration);
        consumer.setExchangeFactory(new TestExchangeFactory());
        return consumer;
    }

    private static class TestExchangeFactory implements ExchangeFactory {

        @Override
        public Exchange createExchange() {
            return createExchange(null, null);
        }

        @Override
        public Exchange createExchange(Headers headers) {
            return createExchange(headers, null);
        }

        @Override
        public Exchange createExchange(Headers headers, Object inputPayload) {
            final Exchange exchange = new Exchange(new SimpleMessage(UUID.randomUUID().toString()), headers);
            if (inputPayload != null) {
                exchange.setInputPayload(inputPayload);
            }
            return exchange;
        }

        @Override
        public Exchange createExchange(Object inputPayload) {
            return createExchange(null, inputPayload);
        }

        @Override
        public Exchange copyExchange(Exchange exchange) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Exchange nextExchange(Exchange current) {
            throw new UnsupportedOperationException();
        }
    }

}
