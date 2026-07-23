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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DefaultFileChannelConfiguration implements FileChannelConfiguration {

    private static final Path DEFAULT_STATE_DIRECTORY =
        Path.of(System.getProperty("user.home"), ".pipelite", "file-channel-adapter");

    private static final FileRecordMapper<String> DEFAULT_MAPPER = new LineRecordMapper();

    private Path stateDirectory;

    private final Map<String, FileRecordMapper<?>> mappers = new HashMap<>();

    @Override
    public void setStateDirectory(Path stateDirectory) {
        this.stateDirectory = Preconditions.notNull(stateDirectory, "stateDirectory is required and cannot be null");
    }

    @Override
    public Path getStateDirectory() {
        return stateDirectory != null ? stateDirectory : DEFAULT_STATE_DIRECTORY;
    }

    @Override
    public void registerMapper(String name, FileRecordMapper<?> mapper) {
        Preconditions.hasText(name, "name is required and cannot be null or empty");
        Preconditions.notNull(mapper, "mapper is required and cannot be null");
        mappers.put(name, mapper);
    }

    @Override
    public FileRecordMapper<?> resolveMapper(String name) {
        if (name == null) {
            return DEFAULT_MAPPER;
        }
        final FileRecordMapper<?> mapper = mappers.get(name);
        if (mapper == null) {
            throw new IllegalArgumentException(String.format("No FileRecordMapper registered under name '%s'", name));
        }
        return mapper;
    }

}
