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
package io.pipelite.spi.inbox;

import io.pipelite.spi.flow.exchange.IdentityGenerator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link DurableInboxProvider}: one {@link SegmentedLogDurableInbox} per distinct
 * resource key, created on first request and cached for the lifetime of this provider — mirrors
 * {@code FileTailStateStore}'s own resource-to-file naming exactly (issue #6/#70): every
 * resource's segment files share one flat base directory, named with the SHA-256 hex digest of
 * the resource key as a file-name prefix (never a subdirectory per resource, which would add
 * nesting without changing contention, blast radius, or lifecycle — all already scoped per file/
 * prefix, not per directory), so no filesystem-unsafe character in the original resource string
 * ever reaches a path segment.
 */
public final class SegmentedLogDurableInboxProvider implements DurableInboxProvider {

    private final Path baseDirectory;
    private final IdentityGenerator identityGenerator;
    private final ConcurrentHashMap<String, DurableInbox> inboxByResource = new ConcurrentHashMap<>();

    public SegmentedLogDurableInboxProvider(Path baseDirectory, IdentityGenerator identityGenerator) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory is required and cannot be null");
        this.identityGenerator = Objects.requireNonNull(identityGenerator, "identityGenerator is required and cannot be null");
    }

    @Override
    public DurableInbox forResource(String resourceKey) {
        Objects.requireNonNull(resourceKey, "resourceKey is required and cannot be null");
        return inboxByResource.computeIfAbsent(resourceKey, key ->
            new SegmentedLogDurableInbox(baseDirectory, sha256Hex(key), identityGenerator));
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
