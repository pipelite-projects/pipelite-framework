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

import io.pipelite.core.Pipelite;
import io.pipelite.dsl.annotation.DefineFlow;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class FlowConfigurationScannerTest {

    private FlowConfigurationScanner subject;
    private DefaultDependencyRegistry dependencyRegistry;

    @Before
    public void setup(){
        subject = new FlowConfigurationScanner();
        dependencyRegistry = new DefaultDependencyRegistry();
    }

    @FlowConfiguration
    public static class NoDependencyFlows {

        @DefineFlow
        public FlowDefinition acquireFlow(){
            return Pipelite.defineFlow("acquire-flow")
                .fromSource("http://ingress")
                .toSink("slf4j://main-logger")
                .build();
        }
    }

    @Test
    public void whenScan_andMethodHasNoParameters_thenInvokeAndReturnFlowDefinition(){

        final List<FlowDefinition> result = subject.scan(NoDependencyFlows.class, dependencyRegistry);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("acquire-flow", result.get(0).getFlowName());
    }

    public static class SomeService {
    }

    @FlowConfiguration
    public static class WithDependencyFlows {

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
    public void whenScan_andMethodHasResolvableParameter_thenResolveAndInvoke(){

        dependencyRegistry.register("service", new SomeService());

        final List<FlowDefinition> result = subject.scan(WithDependencyFlows.class, dependencyRegistry);

        Assert.assertEquals(1, result.size());
    }

    @Test(expected = UnresolvableDependencyException.class)
    public void whenScan_andMethodHasUnresolvableParameter_thenThrow(){
        subject.scan(WithDependencyFlows.class, dependencyRegistry);
    }

    public static class NotAFlowConfiguration {

        @DefineFlow
        public FlowDefinition acquireFlow(){
            return Pipelite.defineFlow("acquire-flow").fromSource("http://ingress").toSink("slf4j://main-logger").build();
        }
    }

    @Test(expected = FlowConfigurationException.class)
    public void whenScan_andClassNotAnnotated_thenThrow(){
        subject.scan(NotAFlowConfiguration.class, dependencyRegistry);
    }

    @FlowConfiguration
    public static class MultipleFlows {

        @DefineFlow
        public FlowDefinition firstFlow(){
            return Pipelite.defineFlow("first-flow").fromSource("http://ingress-1").toSink("slf4j://main-logger").build();
        }

        @DefineFlow
        public FlowDefinition secondFlow(){
            return Pipelite.defineFlow("second-flow").fromSource("http://ingress-2").toSink("slf4j://main-logger").build();
        }
    }

    @Test
    public void whenScan_andMultipleDefineFlowMethods_thenReturnAllFlowDefinitions(){

        final List<FlowDefinition> result = subject.scan(MultipleFlows.class, dependencyRegistry);

        Assert.assertEquals(2, result.size());
    }

}
