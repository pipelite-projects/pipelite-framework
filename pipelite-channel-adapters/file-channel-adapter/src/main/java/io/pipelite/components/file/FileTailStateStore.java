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
import io.pipelite.common.support.fs.LockedFileStore;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/**
 * Persists, one file per tailed resource, the byte offset up to which a resource has
 * already been consumed, together with the count of header lines already skipped
 * (see {@link FileConstants#SKIP_LINES_PROPERTY_NAME}). State files are named after the
 * SHA-256 hex digest of the resource's path, to avoid any filesystem-unsafe character in
 * the original path. Locked I/O is delegated to {@link LockedFileStore}; this class owns the
 * resource-path-to-file-name mapping and the on-disk value format.
 */
public class FileTailStateStore {

    private static final String STATE_FILE_EXTENSION = ".state";
    private static final String INDEX_FILE_NAME = "index.properties";

    private final Path stateDirectory;
    private final LockedFileStore store;

    public FileTailStateStore(Path stateDirectory) {
        this.stateDirectory = Preconditions.notNull(stateDirectory, "stateDirectory is required and cannot be null");
        this.store = new LockedFileStore(stateDirectory);
    }

    public TailState load(String resourcePath) {
        final String fileName = stateFileName(resourcePath);
        registerInIndexIfNew(resourcePath, fileName);
        return store.readLocked(fileName)
            .map(FileTailStateStore::parseState)
            .orElse(TailState.INITIAL);
    }

    public void save(String resourcePath, TailState state) {
        final String fileName = stateFileName(resourcePath);
        registerInIndexIfNew(resourcePath, fileName);
        store.writeLocked(fileName, state.getOffset() + "\n" + state.getSkippedLines());
    }

    private static TailState parseState(String content) {
        final String[] lines = content.split("\n", -1);
        final long offset = parseLongOrDefault(lines.length > 0 ? lines[0] : null, 0L);
        final long skippedLines = parseLongOrDefault(lines.length > 1 ? lines[1] : null, 0L);
        return new TailState(offset, skippedLines);
    }

    private static long parseLongOrDefault(String value, long defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Long.parseLong(value.trim());
    }

    private String stateFileName(String resourcePath) {
        return sha256Hex(resourcePath) + STATE_FILE_EXTENSION;
    }

    private void registerInIndexIfNew(String resourcePath, String fileName) {
        final boolean isNew = !Files.exists(stateDirectory.resolve(fileName));
        if (isNew) {
            updateIndex(resourcePath, fileName);
        }
    }

    private void updateIndex(String resourcePath, String stateFileName) {
        final String key = stateFileName.substring(0, stateFileName.length() - STATE_FILE_EXTENSION.length());
        store.readAndWriteLocked(INDEX_FILE_NAME, currentContent -> {
            final Properties properties = new Properties();
            currentContent.ifPresent(content -> loadProperties(properties, content));
            if (!properties.containsKey(key)) {
                properties.setProperty(key, resourcePath);
            }
            return formatProperties(properties);
        });
    }

    private static void loadProperties(Properties properties, String content) {
        try {
            properties.load(new StringReader(content));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse state index", exception);
        }
    }

    private static String formatProperties(Properties properties) {
        final StringWriter writer = new StringWriter();
        try {
            properties.store(writer, "sha256 -> original resource path (debug only, not read by runtime)");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to format state index", exception);
        }
        return writer.toString();
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
