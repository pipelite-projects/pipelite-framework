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

import java.nio.file.Path;
import java.util.Optional;

public interface FileChannelConfiguration {

    void setStateDirectory(Path stateDirectory);
    Path getStateDirectory();

    /**
     * Confines every {@code file://} sink's target to this directory (issue #60) - {@link
     * FileProducer} rejects, at creation time, any resource whose normalized path resolves
     * outside it. Unset by default (empty), which preserves the pre-#60 behavior of accepting any
     * path: this is an opt-in safety net for a context where the resource may be built from
     * external input (e.g. via a property resolver), not a restriction every caller is forced
     * into.
     */
    void setAllowedWriteDirectory(Path allowedWriteDirectory);
    Optional<Path> getAllowedWriteDirectory();

    void registerMapper(String name, FileRecordMapper<?> mapper);

    /**
     * Resolves a mapper by its logical name. Falls back to a shared {@link LineRecordMapper}
     * if {@code name} is {@code null}.
     *
     * @throws IllegalArgumentException if {@code name} is not {@code null} but not registered
     */
    FileRecordMapper<?> resolveMapper(String name);

}
