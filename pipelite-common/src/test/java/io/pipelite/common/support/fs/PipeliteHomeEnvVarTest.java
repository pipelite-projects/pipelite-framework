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

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * There is no supported way to mutate the current JVM process's environment variables, so the
 * {@code PIPELITE_HOME} branch of {@link PipeliteHome#resolve()} is exercised in a forked child
 * process instead, via {@link PipeliteHomeEnvVarMain}.
 */
public class PipeliteHomeEnvVarTest {

    @Test
    public void shouldHonorPipeliteHomeEnvironmentVariableInChildProcess() throws IOException, InterruptedException {
        final Path expected = Path.of(System.getProperty("java.io.tmpdir"), "pipelite-home-env-var-test");
        final String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        final String classpath = System.getProperty("java.class.path");

        final Process launched = launch(javaBinary, classpath, expected);
        final String output = readOutput(launched);
        final boolean exited = launched.waitFor(30, TimeUnit.SECONDS);

        Assert.assertTrue("child process did not exit in time", exited);
        Assert.assertEquals(0, launched.exitValue());
        Assert.assertEquals(expected.toString(), output.trim());
    }

    private static Process launch(String javaBinary, String classpath, Path pipeliteHome) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(javaBinary, "-cp", classpath, PipeliteHomeEnvVarMain.class.getName());
        builder.environment().put("PIPELITE_HOME", pipeliteHome.toString());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private static String readOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            final StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            return output.toString();
        }
    }

}
