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
import java.util.Optional;

/**
 * Resolves the shared root directory under which any Pipelite component may keep its own
 * durable state ("Pipelite home"). Resolution order: {@code pipelite.home} system property,
 * then {@code PIPELITE_HOME} environment variable, then {@code ~/.pipelite}.
 * <p>
 * Purely computes paths: it never creates directories or touches the filesystem.
 * <p>
 * {@link #resolve(String)} additionally namespaces its result under an optional application id
 * ({@code pipelite.application.id} system property, then {@code PIPELITE_APPLICATION_ID}
 * environment variable — unset by default) so two <em>different</em> Pipelite applications that
 * happen to share the same machine/home directory without ever customizing {@code pipelite.home}
 * don't silently share (and corrupt) each other's durable state — e.g. one application's
 * retry-channel trying to resolve a dump whose {@code flowName} only exists in the other
 * application's flow registry. Set it to something stable and unique per logical application
 * (not per replica/instance — multiple replicas of the <em>same</em> application are expected to
 * share this id and, with it, its durable state).
 */
public final class PipeliteHome {

    private static final String HOME_PROPERTY = "pipelite.home";
    private static final String HOME_ENV = "PIPELITE_HOME";
    private static final String APPLICATION_ID_PROPERTY = "pipelite.application.id";
    private static final String APPLICATION_ID_ENV = "PIPELITE_APPLICATION_ID";
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
        return resolveApplicationId()
            .map(applicationId -> resolve().resolve(applicationId).resolve(subfolder))
            .orElseGet(() -> resolve().resolve(subfolder));
    }

    private static Optional<String> resolveApplicationId() {
        final String sysProp = System.getProperty(APPLICATION_ID_PROPERTY);
        if (sysProp != null && !sysProp.isBlank()) {
            return Optional.of(sysProp);
        }
        final String env = System.getenv(APPLICATION_ID_ENV);
        if (env != null && !env.isBlank()) {
            return Optional.of(env);
        }
        return Optional.empty();
    }

}
