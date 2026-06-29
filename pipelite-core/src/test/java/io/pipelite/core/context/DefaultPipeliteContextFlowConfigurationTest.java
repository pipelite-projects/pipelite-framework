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
package io.pipelite.core.context;

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.annotation.DefineFlow;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public class DefaultPipeliteContextFlowConfigurationTest {

    private DefaultPipeliteContext subject;

    @Before
    public void setup(){
        subject = new DefaultPipeliteContext();
    }

    public static class SomeService {
    }

    @FlowConfiguration
    public static class AcquireFlowConfiguration {

        @DefineFlow
        public FlowDefinition acquireFlow(SomeService service){
            Assert.assertNotNull(service);
            return Pipelite.defineFlow("acquire-flow")
                .fromSource("http://ingress")
                .toSink("slf4j://main-logger")
                .build();
        }
    }

    @Test
    public void whenRegisterFlowConfigurationClass_thenFlowDefinitionIsRegistered(){

        subject.registerDependency("service", new SomeService());
        subject.registerFlowConfigurationClass(AcquireFlowConfiguration.class);

        Assert.assertTrue(subject.isRegistered("acquire-flow"));
    }

    @Test
    public void whenGetFlowDefinition_andRegistered_thenReturnIt(){

        final FlowDefinition flowDefinition = Pipelite.defineFlow("simple-flow")
            .fromSource("http://ingress")
            .toSink("slf4j://main-logger")
            .build();
        subject.registerFlowDefinition(flowDefinition);

        final Optional<FlowDefinition> result = subject.getFlowDefinition("simple-flow");

        Assert.assertTrue(result.isPresent());
        Assert.assertSame(flowDefinition, result.get());
    }

    @Test
    public void whenGetFlowDefinition_andNotRegistered_thenReturnEmpty(){
        Assert.assertFalse(subject.getFlowDefinition("missing-flow").isPresent());
    }

    @Test(expected = DuplicateFlowDefinitionException.class)
    public void whenRegisterFlowDefinition_andNameAlreadyRegistered_thenThrow(){

        final FlowDefinition first = Pipelite.defineFlow("duplicate-flow")
            .fromSource("http://ingress-1")
            .toSink("slf4j://main-logger")
            .build();
        final FlowDefinition second = Pipelite.defineFlow("duplicate-flow")
            .fromSource("http://ingress-2")
            .toSink("slf4j://main-logger")
            .build();

        subject.registerFlowDefinition(first);
        subject.registerFlowDefinition(second);
    }

}
