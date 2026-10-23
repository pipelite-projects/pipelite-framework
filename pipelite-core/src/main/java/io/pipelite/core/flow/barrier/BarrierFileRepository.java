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
package io.pipelite.core.flow.barrier;

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.support.fs.LockedFileStore;
import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.common.support.serialization.ObjectToByteArrayConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Keeps the barriers on disk, in a directory of Pipelite Home: one file for each waiting exchange and one for each
 * completion, each written once and replaced atomically under the file locks of {@link LockedFileStore}. What
 * is read back at the start is the state, so an exchange that was waiting when the process stopped is still
 * waiting when it starts again, and one that was released but not yet resumed is found by {@link #findResolved()}.
 * <p>
 * The logic is the one of {@link BarrierInMemoryRepository}, which holds the state in memory and answers from it;
 * this class writes each change before the operation returns, and reads everything back once, when it is created.
 * A single process is assumed to use the directory.
 * <p>
 * The files are named after a hash of the id or the pair, which can hold any character, and hold the serialized
 * record, base64 encoded.
 */
public class BarrierFileRepository extends BarrierInMemoryRepository {

    private static final String WAITER_EXTENSION = ".waiter";
    private static final String COMPLETION_EXTENSION = ".completion";

    private final Path waitersDirectory;
    private final Path completionsDirectory;
    private final LockedFileStore waitersStore;
    private final LockedFileStore completionsStore;
    private final ObjectToByteArrayConverter serializer = new ObjectToByteArrayConverter();
    private final ByteArrayToObjectConverter deserializer = new ByteArrayToObjectConverter();

    public BarrierFileRepository(Path directory) {
        Preconditions.notNull(directory, "directory is required and cannot be null");
        this.waitersDirectory = directory.resolve("waiters");
        this.completionsDirectory = directory.resolve("completions");
        this.waitersStore = new LockedFileStore(waitersDirectory);
        this.completionsStore = new LockedFileStore(completionsDirectory);
        for (String content : readAll(waitersDirectory, WAITER_EXTENSION, waitersStore)) {
            restore(parse(content, Waiter.class));
        }
        for (String content : readAll(completionsDirectory, COMPLETION_EXTENSION, completionsStore)) {
            restore(parse(content, Completion.class));
        }
        reconcile();
    }

    @Override
    protected void onWaiterStored(Waiter waiter) {
        waitersStore.writeLocked(fileName(waiter.id(), WAITER_EXTENSION), format(waiter));
    }

    @Override
    protected void onWaiterRemoved(Waiter waiter) {
        waitersStore.deleteLocked(fileName(waiter.id(), WAITER_EXTENSION));
    }

    @Override
    protected void onCompletionStored(Completion completion) {
        completionsStore.writeLocked(fileName(identityOf(completion.of()), COMPLETION_EXTENSION), format(completion));
    }

    @Override
    protected void onCompletionRemoved(Completion completion) {
        completionsStore.deleteLocked(fileName(identityOf(completion.of()), COMPLETION_EXTENSION));
    }

    private java.util.List<String> readAll(Path directory, String extension, LockedFileStore store) {
        if (!Files.isDirectory(directory)) {
            return java.util.List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(extension))
                .map(store::readLocked)
                .flatMap(Optional::stream)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to list directory '%s'", directory), exception);
        }
    }

    private String format(Object record) {
        return BaseEncoding.base64().encode(serializer.convert(record));
    }

    private <T> T parse(String content, Class<T> type) {
        return deserializer.convert(BaseEncoding.base64().decode(content), type);
    }

    private static String identityOf(Pending pending) {
        return pending.flow() + '\u0000' + pending.key();
    }

    private static String fileName(String identity, String extension) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(identity.getBytes(StandardCharsets.UTF_8))) + extension;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
