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

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;

public class DefaultFileChannelConfigurationTest {

    private final DefaultFileChannelConfiguration subject = new DefaultFileChannelConfiguration();

    @Test
    public void shouldResolveDefaultLineRecordMapperWhenNameIsNull() {
        final FileRecordMapper<?> mapper = subject.resolveMapper(null);
        Assert.assertTrue(mapper instanceof LineRecordMapper);
    }

    @Test
    public void shouldResolveRegisteredMapperByLogicalName() {
        final FileRecordMapper<String> customMapper = new LineRecordMapper();
        subject.registerMapper("custom", customMapper);

        Assert.assertSame(customMapper, subject.resolveMapper("custom"));
    }

    @Test
    public void shouldThrowWhenResolvingUnregisteredMapperName() {
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.resolveMapper("does-not-exist"));
    }

    @Test
    public void shouldRejectRegistrationWithBlankName() {
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.registerMapper(" ", new LineRecordMapper()));
    }

    @Test
    public void shouldRejectRegistrationWithNullMapper() {
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.registerMapper("name", null));
    }

    @Test
    public void shouldExposeDefaultStateDirectoryUnderUserHome() {
        final Path stateDirectory = subject.getStateDirectory();
        Assert.assertTrue(stateDirectory.startsWith(Path.of(System.getProperty("user.home"))));
        Assert.assertTrue(stateDirectory.toString().contains("file-channel-adapter"));
    }

    @Test
    public void shouldHonorExplicitStateDirectoryOverride() {
        final Path override = Path.of(System.getProperty("java.io.tmpdir"), "custom-state-dir");
        subject.setStateDirectory(override);
        Assert.assertEquals(override, subject.getStateDirectory());
    }

}
