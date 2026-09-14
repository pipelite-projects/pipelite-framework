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
package io.pipelite.core.flow.execution.dump;

import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

public class FileFlowExecutionDumpRepositoryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path directory;
    private FlowExecutionDumpRepository subject;

    @Before
    public void setup() {
        directory = temporaryFolder.getRoot().toPath().resolve("flow-execution-dumps");
        subject = new FileFlowExecutionDumpRepository(directory);
    }

    private static SerializedFlowExecutionDump aDump(String id, LocalDateTime creationTime) {
        final SerializedFlowExecutionDump dump = new SerializedFlowExecutionDump(id, "flow-hash", "a-flow", creationTime);
        dump.setSourceEndpointResource("a-source");
        dump.setLastExecutedProcessor("a-processor");
        dump.setFailedProcessor("the-failing-processor");
        dump.setAttemptNumber(2);
        dump.setStackTrace("java.lang.RuntimeException: boom\n\tat Somewhere.java:1");
        dump.setMaxAttempts(5);
        dump.setDeadLetterFlowName("a-dead-letter-flow");
        dump.setExchangeData("c29tZS1wYXlsb2Fk", "base64");
        return dump;
    }

    @Test
    public void shouldReturnEmptyWhenLoadingAnUnknownId() {
        Assert.assertEquals(Optional.empty(), subject.tryLoad("unknown-id"));
    }

    @Test
    public void shouldRoundTripEveryFieldThroughSaveAndTryLoad() {

        final SerializedFlowExecutionDump saved = aDump("dump-1", LocalDateTime.of(2026, 9, 10, 12, 30, 0));
        subject.save(saved);

        final FlowExecutionDump loaded = subject.tryLoad("dump-1").orElseThrow();

        Assert.assertEquals(saved.getId(), loaded.getId());
        Assert.assertEquals(saved.getCreationTime(), loaded.getCreationTime());
        Assert.assertEquals(saved.getFlowHash(), loaded.getFlowHash());
        Assert.assertEquals(saved.getFlowName(), loaded.getFlowName());
        Assert.assertEquals(saved.getSourceEndpointResource(), loaded.getSourceEndpointResource());
        Assert.assertEquals(saved.getLastExecutedProcessor(), loaded.getLastExecutedProcessor());
        Assert.assertEquals(saved.getFailedProcessor(), loaded.getFailedProcessor());
        Assert.assertEquals(saved.getAttemptNumber(), loaded.getAttemptNumber());
        Assert.assertEquals(saved.getStackTrace(), loaded.getStackTrace());
        Assert.assertEquals(saved.getMaxAttempts(), loaded.getMaxAttempts());
        Assert.assertEquals(saved.getDeadLetterFlowName(), loaded.getDeadLetterFlowName());
        Assert.assertEquals(saved.getExchangeData(), ((SerializedFlowExecutionDump) loaded).getExchangeData());
        Assert.assertEquals(saved.getEncoding(), ((SerializedFlowExecutionDump) loaded).getEncoding());
    }

    @Test
    public void shouldRoundTripNullDeadLetterFlowNameAndFailedProcessorAsNullNotEmptyString() {

        final SerializedFlowExecutionDump saved = aDump("dump-2", LocalDateTime.now());
        saved.setDeadLetterFlowName(null);
        saved.setFailedProcessor(null);
        subject.save(saved);

        final FlowExecutionDump loaded = subject.tryLoad("dump-2").orElseThrow();

        Assert.assertNull("deadLetterFlowName must round-trip as null, not empty string", loaded.getDeadLetterFlowName());
        Assert.assertNull("failedProcessor must round-trip as null, not empty string", loaded.getFailedProcessor());
    }

    @Test
    public void shouldReturnTheOldestPendingDumpFromPoll() {

        subject.save(aDump("newer", LocalDateTime.of(2026, 9, 10, 12, 0, 0)));
        subject.save(aDump("oldest", LocalDateTime.of(2026, 9, 10, 10, 0, 0)));
        subject.save(aDump("middle", LocalDateTime.of(2026, 9, 10, 11, 0, 0)));

        final FlowExecutionDump polled = subject.poll().orElseThrow();

        Assert.assertEquals("oldest", polled.getId());
    }

    @Test
    public void shouldReturnEmptyFromPollWhenNothingWasEverSaved() {
        Assert.assertEquals(Optional.empty(), subject.poll());
    }

    @Test
    public void shouldNoLongerBeFoundAfterRemove() {

        subject.save(aDump("dump-3", LocalDateTime.now()));
        subject.remove("dump-3");

        Assert.assertEquals(Optional.empty(), subject.tryLoad("dump-3"));
        Assert.assertEquals(Optional.empty(), subject.poll());
    }

    @Test
    public void shouldNotThrowWhenRemovingAnUnknownId() {
        subject.remove("never-saved");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSavingAFlowExecutionDumpThatIsNotSerializedFlowExecutionDump() {
        subject.save(new DefaultFlowExecutionDump("id", "hash", "flow", LocalDateTime.now()));
    }

}
