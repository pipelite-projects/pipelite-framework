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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Keeps the barriers on disk, in a directory of Pipelite Home: one file per barrier and key, replaced
 * atomically and under the file locks of {@link LockedFileStore}, so a state survives a restart and an update
 * is never seen half done. This is what makes an exchange that waits for the others safe once the arrival is
 * acknowledged to its inbox.
 * <p>
 * A state is the serialized {@link BarrierState}, base64 encoded, so the payloads that wait must be
 * serializable, like everything else the framework writes (the inbox, the retry dumps). An arrival whose
 * payload is not fails with the exception of the serialization, before anything is written.
 * <p>
 * The file is named after a hash of the barrier and the key, which can hold any character. Every change
 * rewrites the file of that key, so a barrier waiting for very many contributions of very large payloads is
 * not what this is for.
 */
public class BarrierFileRepository implements BarrierRepository {

    private static final String FILE_EXTENSION = ".barrier";

    private final Path directory;
    private final LockedFileStore store;
    private final ObjectToByteArrayConverter serializer = new ObjectToByteArrayConverter();
    private final ByteArrayToObjectConverter deserializer = new ByteArrayToObjectConverter();

    public BarrierFileRepository(Path directory) {
        this.directory = Preconditions.notNull(directory, "directory is required and cannot be null");
        this.store = new LockedFileStore(directory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R update(String barrierId, String key, Supplier<BarrierState> onAbsent, Function<BarrierState, R> action) {
        final Object[] result = new Object[1];
        store.readAndWriteLocked(fileName(barrierId, key), current -> {
            final BarrierState state = current.map(this::parse).orElseGet(onAbsent);
            if (state == null) {
                return null;   // "no update": nothing is created
            }
            result[0] = action.apply(state);
            return format(state);
        });
        return (R) result[0];
    }

    @Override
    public Optional<BarrierState> tryLoad(String barrierId, String key) {
        return store.readLocked(fileName(barrierId, key)).map(this::parse);
    }

    @Override
    public List<BarrierState> findExpired(Instant now) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(FILE_EXTENSION))
                .map(store::readLocked)
                .flatMap(Optional::stream)
                .map(this::parse)
                .filter(state -> !state.getExpiresAt().isAfter(now))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to list directory '%s'", directory), exception);
        }
    }

    @Override
    public void remove(String barrierId, String key) {
        store.deleteLocked(fileName(barrierId, key));
    }

    private String format(BarrierState state) {
        return BaseEncoding.base64().encode(serializer.convert(state));
    }

    private BarrierState parse(String content) {
        return deserializer.convert(BaseEncoding.base64().decode(content), BarrierState.class);
    }

    private static String fileName(String barrierId, String key) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(barrierId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest()) + FILE_EXTENSION;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
