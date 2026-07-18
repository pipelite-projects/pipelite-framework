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

import io.pipelite.common.support.Preconditions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/**
 * Persists, one file per tailed resource, the byte offset up to which a resource has
 * already been consumed. State files are named after the SHA-256 hex digest of the
 * resource's path, to avoid any filesystem-unsafe character in the original path.
 */
public class FileTailStateStore {

    private static final String STATE_FILE_EXTENSION = ".state";
    private static final String INDEX_FILE_NAME = "index.properties";

    private final Path stateDirectory;

    public FileTailStateStore(Path stateDirectory) {
        this.stateDirectory = Preconditions.notNull(stateDirectory, "stateDirectory is required and cannot be null");
    }

    public long load(String resourcePath) {
        final Path stateFile = resolveStateFile(resourcePath);
        final boolean isNew = !Files.exists(stateFile);
        ensureStateDirectory();
        try (FileChannel fc = FileChannel.open(stateFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
             FileLock lock = fc.lock()) {
            if (isNew) {
                updateIndex(resourcePath, stateFile);
            }
            if (fc.size() == 0) {
                return 0L;
            }
            final String content = readAsText(fc).trim();
            return content.isEmpty() ? 0L : Long.parseLong(content);
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to load tail state for resource '%s'", resourcePath), exception);
        }
    }

    public void save(String resourcePath, long offset) {
        final Path stateFile = resolveStateFile(resourcePath);
        final boolean isNew = !Files.exists(stateFile);
        ensureStateDirectory();
        try (FileChannel fc = FileChannel.open(stateFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
             FileLock lock = fc.lock()) {
            if (isNew) {
                updateIndex(resourcePath, stateFile);
            }
            fc.truncate(0);
            fc.position(0);
            fc.write(ByteBuffer.wrap(Long.toString(offset).getBytes(StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to save tail state for resource '%s'", resourcePath), exception);
        }
    }

    private void ensureStateDirectory() {
        try {
            Files.createDirectories(stateDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to create state directory '%s'", stateDirectory), exception);
        }
    }

    private Path resolveStateFile(String resourcePath) {
        return stateDirectory.resolve(sha256Hex(resourcePath) + STATE_FILE_EXTENSION);
    }

    private void updateIndex(String resourcePath, Path stateFile) {
        final Path indexFile = stateDirectory.resolve(INDEX_FILE_NAME);
        final String fileName = stateFile.getFileName().toString();
        final String key = fileName.substring(0, fileName.length() - STATE_FILE_EXTENSION.length());
        try (FileChannel fc = FileChannel.open(indexFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
             FileLock lock = fc.lock()) {
            final Properties properties = new Properties();
            if (fc.size() > 0) {
                properties.load(new ByteArrayInputStream(readAsBytes(fc)));
            }
            if (!properties.containsKey(key)) {
                properties.setProperty(key, resourcePath);
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                properties.store(out, "sha256 -> original resource path (debug only, not read by runtime)");
                fc.position(0);
                fc.truncate(0);
                fc.write(ByteBuffer.wrap(out.toByteArray()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to update state index for resource '%s'", resourcePath), exception);
        }
    }

    private static byte[] readAsBytes(FileChannel fc) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate((int) fc.size());
        fc.position(0);
        while (buffer.hasRemaining() && fc.read(buffer) != -1) {
            // keep reading until the buffer is full
        }
        return buffer.array();
    }

    private static String readAsText(FileChannel fc) throws IOException {
        return new String(readAsBytes(fc), StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

}
