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

/**
 * Not a test by itself: launched as a child process by {@link PipeliteHomeEnvVarTest} to
 * exercise the {@code PIPELITE_HOME} environment variable branch of {@link PipeliteHome#resolve()},
 * which cannot be set for the current JVM process through any supported API.
 */
public final class PipeliteHomeEnvVarMain {

    private PipeliteHomeEnvVarMain() {
    }

    public static void main(String[] args) {
        System.out.println(PipeliteHome.resolve());
    }

}
