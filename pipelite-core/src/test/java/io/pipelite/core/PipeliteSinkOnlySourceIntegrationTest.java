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
package io.pipelite.core;

import io.pipelite.core.context.PipeliteContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Issue #159: an endpoint that cannot be read from refuses to be a source, clearly and when the context
 * starts. Before, every endpoint could create a consumer by default, so a flow reading from a channel
 * that only writes ({@code fromSource("slf4j://...")}) started and then never received anything.
 */
public class PipeliteSinkOnlySourceIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = Pipelite.createContext();
    }

    @After
    public void tearDown() {
        context.stop();
    }

    @Test
    public void givenAFlowReadsFromAChannelThatOnlyWrites_thenTheContextFailsToStartSayingSo() {
        context.registerFlowDefinition(Pipelite.defineFlow("reads-from-a-logger")
            .fromSource("slf4j://some-logger")
            .process("never-reached", (exchange, contribution) -> { })
            .build());

        try {
            context.start();
            Assert.fail("a flow cannot read from a channel that only writes");
        } catch (UnsupportedOperationException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("cannot be used as a source"));
        }
    }
}
