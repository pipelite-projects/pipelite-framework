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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wires a {@link FileTailPollingConsumer} (source) directly to a {@link FileProducer} (sink),
 * without going through the full flow/DSL machinery (kept out of this module's dependencies,
 * see D4 in the implementation plan). This still exercises the source-to-sink data path and the
 * endpoint URL configuration options (record mapper resolution included) end-to-end.
 */
public class FileSourceToSinkTest {

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
    public void shouldStreamExistingLinesFromSourceToSink() throws IOException {
        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");
        Files.writeString(sourceFile, "line-1\nline-2\nline-3\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer consumer = newConsumer(sourceFile, "?startPosition=beginning");
        final FileProducer producer = newProducer(sinkFile, true);

        final List<Object> transferred = drainWithLineBreaks(consumer, producer);

        Assert.assertEquals(List.of("line-1", "line-2", "line-3"), transferred);
        Assert.assertEquals("line-1\nline-2\nline-3\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldStreamNewlyAppendedLinesAcrossMultiplePollsIntoTheSink() throws IOException {
        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");
        Files.writeString(sourceFile, "skipped-because-startPosition-is-end\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer consumer = newConsumer(sourceFile, "");
        final FileProducer producer = newProducer(sinkFile, true);

        drainWithLineBreaks(consumer, producer);
        Assert.assertFalse("Nothing should have reached the sink on the first (empty) drain", Files.exists(sinkFile));

        Files.writeString(sourceFile, "appended-1\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        drainWithLineBreaks(consumer, producer);

        Files.writeString(sourceFile, "appended-2\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        drainWithLineBreaks(consumer, producer);

        Assert.assertEquals("appended-1\nappended-2\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldApplyRecordMapperRegisteredAndSelectedViaQueryParameter() throws IOException {
        configuration.registerMapper("upper", new UpperCaseLineRecordMapper());

        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");
        Files.writeString(sourceFile, "hello\nworld\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer consumer = newConsumer(sourceFile, "?startPosition=beginning&recordMapper=upper");
        final FileProducer producer = newProducer(sinkFile, true);

        drainWithLineBreaks(consumer, producer);

        Assert.assertEquals("HELLO\nWORLD\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldUseDefaultLineRecordMapperWhenQueryParameterIsAbsentEvenIfOtherMappersAreRegistered() throws IOException {
        configuration.registerMapper("upper", new UpperCaseLineRecordMapper());

        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");
        Files.writeString(sourceFile, "hello\nworld\n", StandardCharsets.UTF_8);

        final FileTailPollingConsumer consumer = newConsumer(sourceFile, "?startPosition=beginning");
        final FileProducer producer = newProducer(sinkFile, true);

        drainWithLineBreaks(consumer, producer);

        Assert.assertEquals("hello\nworld\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldFailFastWhenRecordMapperQueryParameterReferencesAnUnregisteredName() {
        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        Assert.assertThrows(IllegalArgumentException.class,
            () -> newConsumer(sourceFile, "?recordMapper=does-not-exist"));
    }

    /**
     * LineRecordMapper strips line terminators by design (D2/Fase 2b); re-appending "\n" here
     * simulates the minimal processor a real flow would place between source and sink to
     * preserve line boundaries in the sink file.
     */
    private List<Object> drainWithLineBreaks(FileTailPollingConsumer consumer, FileProducer producer) {
        final List<Object> transferred = new ArrayList<>();
        Exchange exchange;
        while ((exchange = consumer.receive(0)) != null) {
            final Object record = exchange.getInputPayload();
            transferred.add(record);
            exchange.setInputPayload(record + "\n");
            producer.process(exchange);
        }
        return transferred;
    }

    private FileTailPollingConsumer newConsumer(Path file, String query) {
        final EndpointURL endpointURL = EndpointURL.parse(file.toString() + query, RESOURCE_PATTERN);
        final Endpoint endpoint = new DefaultEndpoint(endpointURL);
        final FileTailPollingConsumer consumer = new FileTailPollingConsumer(endpoint, configuration);
        consumer.setExchangeFactory(new TestExchangeFactory());
        return consumer;
    }

    private FileProducer newProducer(Path file, boolean append) {
        final String query = append ? "?append=true" : "";
        final EndpointURL endpointURL = EndpointURL.parse(file.toString() + query, RESOURCE_PATTERN);
        final Endpoint endpoint = new DefaultEndpoint(endpointURL);
        return new FileProducer(endpoint);
    }

    private static class UpperCaseLineRecordMapper implements FileRecordMapper<String> {

        private final LineRecordMapper delegate = new LineRecordMapper();

        @Override
        public MappingResult<String> map(String newContent) {
            final MappingResult<String> result = delegate.map(newContent);
            final List<String> upperCased = new ArrayList<>(result.getRecords().size());
            for (String record : result.getRecords()) {
                upperCased.add(record.toUpperCase());
            }
            return new MappingResult<>(upperCased, result.getConsumedLength());
        }
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
