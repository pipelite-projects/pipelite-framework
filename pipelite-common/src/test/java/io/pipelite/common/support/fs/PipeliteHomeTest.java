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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class PipeliteHomeTest {

    @After
    public void cleanup() {
        System.clearProperty("pipelite.home");
    }

    @Test
    public void shouldDefaultToDotPipeliteUnderUserHomeWhenNothingIsConfigured() {
        final Path resolved = PipeliteHome.resolve();
        Assert.assertEquals(Path.of(System.getProperty("user.home"), ".pipelite"), resolved);
    }

    @Test
    public void shouldHonorPipeliteHomeSystemPropertyOverride() {
        System.setProperty("pipelite.home", "/custom/pipelite-home");
        Assert.assertEquals(Path.of("/custom/pipelite-home"), PipeliteHome.resolve());
    }

    @Test
    public void shouldIgnoreBlankSystemPropertyAndFallBackToDefault() {
        System.setProperty("pipelite.home", "   ");
        Assert.assertEquals(Path.of(System.getProperty("user.home"), ".pipelite"), PipeliteHome.resolve());
    }

    @Test
    public void shouldResolveSubfolderUnderHome() {
        System.setProperty("pipelite.home", "/custom/pipelite-home");
        Assert.assertEquals(Path.of("/custom/pipelite-home", "file-channel-adapter"), PipeliteHome.resolve("file-channel-adapter"));
    }

    @Test
    public void shouldNeverCreateAnyDirectoryOnDisk() {
        System.setProperty("pipelite.home", "/should/never/be/created/by/resolve");
        final Path resolved = PipeliteHome.resolve("subfolder");
        Assert.assertFalse(Files.exists(resolved));
        Assert.assertFalse(Files.exists(resolved.getParent()));
    }

}
