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
package io.pipelite.components.http.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Issues #48/#52: port, host and max entity size are now configurable instead of the port being
 * hardcoded to 80 and the body size being unbounded.
 */
public class DefaultHttpChannelConfigurationTest {

    private DefaultHttpChannelConfiguration subject;

    @Before
    public void setup() {
        subject = new DefaultHttpChannelConfiguration();
    }

    @Test
    public void defaultsToPort8080NotThePreviouslyHardcoded80() {
        Assert.assertEquals(8080, subject.getPort());
    }

    @Test
    public void defaultsToAllInterfaces() {
        Assert.assertEquals("0.0.0.0", subject.getHost());
    }

    @Test
    public void defaultsToAFiniteMaxEntitySizeNotUnbounded() {
        Assert.assertEquals(DefaultHttpChannelConfiguration.DEFAULT_MAX_ENTITY_SIZE, subject.getMaxEntitySize());
    }

    @Test
    public void honorsAnExplicitlyConfiguredPortHostAndMaxEntitySize() {
        subject.setPort(9090);
        subject.setHost("127.0.0.1");
        subject.setMaxEntitySize(1024);

        Assert.assertEquals(9090, subject.getPort());
        Assert.assertEquals("127.0.0.1", subject.getHost());
        Assert.assertEquals(1024, subject.getMaxEntitySize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAPortBelowOne() {
        subject.setPort(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAPortAbove65535() {
        subject.setPort(65536);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsABlankHost() {
        subject.setHost("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsANonPositiveMaxEntitySize() {
        subject.setMaxEntitySize(0);
    }

}
