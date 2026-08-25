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
package io.pipelite.common.support.fs;

import java.nio.file.Path;

/**
 * Resolves the shared root directory under which any Pipelite component may keep its own
 * durable state ("Pipelite home"). Resolution order: {@code pipelite.home} system property,
 * then {@code PIPELITE_HOME} environment variable, then {@code ~/.pipelite}.
 * <p>
 * Purely computes paths: it never creates directories or touches the filesystem.
 */
public final class PipeliteHome {

    private static final String HOME_PROPERTY = "pipelite.home";
    private static final String HOME_ENV = "PIPELITE_HOME";
    private static final String DEFAULT_DIR_NAME = ".pipelite";

    private PipeliteHome() {
    }

    public static Path resolve() {
        final String sysProp = System.getProperty(HOME_PROPERTY);
        if (sysProp != null && !sysProp.isBlank()) {
            return Path.of(sysProp);
        }
        final String env = System.getenv(HOME_ENV);
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), DEFAULT_DIR_NAME);
    }

    public static Path resolve(String subfolder) {
        return resolve().resolve(subfolder);
    }

}
