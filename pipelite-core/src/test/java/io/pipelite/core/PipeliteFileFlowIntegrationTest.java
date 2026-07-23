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
package io.pipelite.core;

import io.pipelite.components.file.FileChannelConfigurer;
import io.pipelite.components.file.FileRecordMapper;
import io.pipelite.components.file.LineRecordMapper;
import io.pipelite.components.file.MappingResult;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import org.awaitility.Awaitility;
import org.junit.After;
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
import java.util.concurrent.TimeUnit;

/**
 * Defines a real {@code Flow} via the Pipelite DSL with a {@code file://} source and a
 * {@code file://} sink, and drives it through {@link PipeliteContext#start()}, exercising the
 * file-channel-adapter's tail-polling consumer and producer exactly as a user application would
 * wire them, instead of connecting the SPI classes manually (see {@code FileSourceToSinkTest} in
 * file-channel-adapter for the manually-wired equivalent).
 */
public class PipeliteFileFlowIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    /**
     * Only the first {@link FileChannelConfigurer} registered on a context is ever applied (see
     * {@code DefaultChannelAdapterManager#registerChannelAdapter}, which picks the first matching
     * configurer and ignores the rest) — so tests that need more than the default state directory
     * must fold every setting into a single configurer instead of calling this twice.
     */
    private void useStateDirectory(PipeliteContext target, Path stateDirectory) {
        target.addChannelConfigurer((FileChannelConfigurer) configuration -> configuration.setStateDirectory(stateDirectory));
    }

    @After
    public void tearDown() {
        context.stop();
    }

    @Test
    public void shouldStreamAppendedLinesFromFileSourceToFileSink() throws IOException {

        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");

        useStateDirectory(context, temporaryFolder.getRoot().toPath().resolve("state"));

        final FlowDefinition flow = Pipelite.defineFlow("file-to-file")
            .fromSource("file://" + sourceFile + "?startPosition=beginning&period=100")
            .process("restore-line-break", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class) + "\n"))
            .toSink("file://" + sinkFile + "?append=true")
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        Files.writeString(sourceFile, "line-1\n", StandardCharsets.UTF_8);
        Files.writeString(sourceFile, "line-2\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> Files.exists(sinkFile) && "line-1\nline-2\n".equals(Files.readString(sinkFile, StandardCharsets.UTF_8)));

        Assert.assertEquals("line-1\nline-2\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldSkipPreExistingContentAndStreamOnlyLinesAppendedAfterStartWhenStartPositionIsDefault() throws IOException {
        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");

        Files.writeString(sourceFile, "already-there-before-start\n", StandardCharsets.UTF_8);

        useStateDirectory(context, temporaryFolder.getRoot().toPath().resolve("state"));

        final FlowDefinition flow = Pipelite.defineFlow("file-to-file-tail-from-end")
            .fromSource("file://" + sourceFile + "?period=100")
            .process("restore-line-break", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class) + "\n"))
            .toSink("file://" + sinkFile + "?append=true")
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        // Default startPosition ("end") only establishes its "already consumed" baseline on the
        // first poll after the file is discovered. Appending immediately after start() risks a
        // race where the write lands before that first poll runs and gets silently folded into
        // the baseline instead of being emitted (see project notes on the file-channel-adapter
        // tail consumer). Let at least one full poll cycle (period=100ms) elapse first.
        Awaitility.await().pollDelay(300, TimeUnit.MILLISECONDS).until(() -> true);

        Files.writeString(sourceFile, "appended-after-start\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> Files.exists(sinkFile) && "appended-after-start\n".equals(Files.readString(sinkFile, StandardCharsets.UTF_8)));

        Assert.assertEquals("appended-after-start\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldApplyCustomRecordMapperRegisteredViaChannelConfigurerAndSelectedByQueryParameter() throws IOException {
        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");

        context.addChannelConfigurer((FileChannelConfigurer) configuration -> {
            configuration.setStateDirectory(temporaryFolder.getRoot().toPath().resolve("state"));
            configuration.registerMapper("upper", new UpperCaseLineRecordMapper());
        });

        final FlowDefinition flow = Pipelite.defineFlow("file-to-file-custom-mapper")
            .fromSource("file://" + sourceFile + "?startPosition=beginning&period=100&recordMapper=upper")
            .process("restore-line-break", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class) + "\n"))
            .toSink("file://" + sinkFile + "?append=true")
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        Files.writeString(sourceFile, "hello\nworld\n", StandardCharsets.UTF_8);

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> Files.exists(sinkFile) && "HELLO\nWORLD\n".equals(Files.readString(sinkFile, StandardCharsets.UTF_8)));

        Assert.assertEquals("HELLO\nWORLD\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldResumeFromPersistedOffsetAfterContextRestartWithoutReemittingAlreadyConsumedLines() throws IOException {
        final Path sourceFile = temporaryFolder.getRoot().toPath().resolve("source.log");
        final Path sinkFile = temporaryFolder.getRoot().toPath().resolve("sink.log");
        final Path stateDirectory = temporaryFolder.getRoot().toPath().resolve("state");

        useStateDirectory(context, stateDirectory);

        Files.writeString(sourceFile, "line-1\n", StandardCharsets.UTF_8);

        context.registerFlowDefinition(newRestartableFlow(sourceFile, sinkFile));
        context.start();

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> Files.exists(sinkFile) && "line-1\n".equals(Files.readString(sinkFile, StandardCharsets.UTF_8)));

        context.stop();

        // Simulate an application restart: brand new context instance pointed at the same state
        // directory, with the source file appended to while the "application" was down.
        Files.writeString(sourceFile, "line-2\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        final PipeliteContext restartedContext = new DefaultPipeliteContext();
        useStateDirectory(restartedContext, stateDirectory);
        restartedContext.registerFlowDefinition(newRestartableFlow(sourceFile, sinkFile));
        restartedContext.start();
        try {
            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> "line-1\nline-2\n".equals(Files.readString(sinkFile, StandardCharsets.UTF_8)));
            Assert.assertEquals("line-1\nline-2\n", Files.readString(sinkFile, StandardCharsets.UTF_8));
        } finally {
            restartedContext.stop();
        }
    }

    private FlowDefinition newRestartableFlow(Path sourceFile, Path sinkFile) {
        return Pipelite.defineFlow("file-to-file-restart")
            .fromSource("file://" + sourceFile + "?startPosition=beginning&period=100")
            .process("restore-line-break", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class) + "\n"))
            .toSink("file://" + sinkFile + "?append=true")
            .build();
    }

    /**
     * Minimal custom {@link FileRecordMapper} used to verify that mappers registered via
     * {@link FileChannelConfigurer} and selected through the {@code recordMapper} query
     * parameter are actually wired end-to-end when the source is declared through the DSL.
     */
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

}
