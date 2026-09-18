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
package io.pipelite.core.flow.execution.deadletter;

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.support.fs.LockedFileStore;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Properties;

/**
 * File-backed {@link DeadLetterQueueRepository} (issue #93): one {@code .dlq} file per entry
 * under a caller-supplied directory ({@code DefaultPipeliteContext}'s default is {@code
 * PipeliteHome.resolve("state/dlq")}), via {@link LockedFileStore} - mirrors {@code
 * FileFlowExecutionDumpRepository}'s "one file per entry" shape (not {@code
 * FileDurableInboxDeadLetterWriter}'s single-ever-appended-file shape), since individual entries
 * should be independently listable/inspectable by a future UI.
 */
public class FileDeadLetterQueueRepository implements DeadLetterQueueRepository {

    private static final String FILE_EXTENSION = ".dlq";

    private static final String ID_KEY = "id";
    private static final String CREATION_TIME_KEY = "creationTime";
    private static final String FLOW_NAME_KEY = "flowName";
    private static final String SOURCE_ENDPOINT_RESOURCE_KEY = "sourceEndpointResource";
    private static final String FAILED_PROCESSOR_KEY = "failedProcessor";
    private static final String EXCEPTION_TYPE_KEY = "exceptionType";
    private static final String EXCEPTION_MESSAGE_KEY = "exceptionMessage";
    private static final String STACK_TRACE_KEY = "stackTrace";
    private static final String EXCHANGE_DATA_KEY = "exchangeData";
    private static final String ENCODING_KEY = "encoding";

    private final LockedFileStore store;

    public FileDeadLetterQueueRepository(Path directory) {
        Preconditions.notNull(directory, "directory is required and cannot be null");
        this.store = new LockedFileStore(directory);
    }

    @Override
    public void save(DeadLetteredExchange entry) {
        store.writeLocked(fileName(entry.getId()), format(entry));
    }

    private static String fileName(String id) {
        return id + FILE_EXTENSION;
    }

    private static String format(DeadLetteredExchange entry) {

        final Properties properties = new Properties();
        properties.setProperty(ID_KEY, entry.getId());
        properties.setProperty(CREATION_TIME_KEY, entry.getCreationTime().toString());
        properties.setProperty(FLOW_NAME_KEY, entry.getFlowName());
        putIfNotNull(properties, SOURCE_ENDPOINT_RESOURCE_KEY, entry.getSourceEndpointResource());
        putIfNotNull(properties, FAILED_PROCESSOR_KEY, entry.getFailedProcessor());
        putIfNotNull(properties, EXCEPTION_TYPE_KEY, entry.getExceptionType());
        putIfNotNull(properties, EXCEPTION_MESSAGE_KEY, entry.getExceptionMessage());
        putIfNotNull(properties, STACK_TRACE_KEY, entry.getStackTrace());
        putIfNotNull(properties, EXCHANGE_DATA_KEY, entry.getExchangeData());
        putIfNotNull(properties, ENCODING_KEY, entry.getEncoding());

        final StringWriter writer = new StringWriter();
        try {
            properties.store(writer, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to format dead-lettered exchange", exception);
        }
        return writer.toString();
    }

    private static void putIfNotNull(Properties properties, String key, String value) {
        if (value != null) {
            properties.setProperty(key, value);
        }
    }

}
