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

public interface FileChannelConfiguration {

    void setStateDirectory(Path stateDirectory);
    Path getStateDirectory();

    void registerMapper(String name, FileRecordMapper<?> mapper);

    /**
     * Resolves a mapper by its logical name. Falls back to a shared {@link LineRecordMapper}
     * if {@code name} is {@code null}.
     *
     * @throws IllegalArgumentException if {@code name} is not {@code null} but not registered
     */
    FileRecordMapper<?> resolveMapper(String name);

}
