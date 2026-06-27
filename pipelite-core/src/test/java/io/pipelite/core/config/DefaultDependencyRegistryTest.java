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
package io.pipelite.core.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public class DefaultDependencyRegistryTest {

    private DefaultDependencyRegistry subject;

    @Before
    public void setup(){
        subject = new DefaultDependencyRegistry();
    }

    @Test
    public void whenResolveByType_andSingleMatch_thenReturnIt(){

        final String instance = "some-dependency";
        subject.register("primary", instance);

        final Optional<Object> resolved = subject.resolve(String.class);

        Assert.assertTrue(resolved.isPresent());
        Assert.assertSame(instance, resolved.get());
    }

    @Test
    public void whenResolveByType_andNoMatch_thenReturnEmpty(){
        Assert.assertFalse(subject.resolve(String.class).isPresent());
    }

    @Test(expected = AmbiguousDependencyException.class)
    public void whenResolveByType_andMultipleMatches_thenThrow(){
        subject.register("primary", "dependency-a");
        subject.register("secondary", "dependency-b");
        subject.resolve(String.class);
    }

    @Test
    public void whenResolveByName_thenReturnRegisteredInstance(){

        final String instance = "some-dependency";
        subject.register("primary", instance);

        final Optional<Object> resolved = subject.resolve("primary");

        Assert.assertTrue(resolved.isPresent());
        Assert.assertSame(instance, resolved.get());
    }

    @Test
    public void whenResolveByName_andNotRegistered_thenReturnEmpty(){
        Assert.assertFalse(subject.resolve("missing").isPresent());
    }

}
