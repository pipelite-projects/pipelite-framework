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
package io.pipelite.spring;

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spring.context.PipeliteContextInitializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {EnablePipeliteIntegrationTest.TestConfiguration.class})
public class EnablePipeliteIntegrationTest {

    @Autowired
    private PipeliteContext pipeliteContext;

    @Autowired
    private TestConfiguration configuration;

    @Before
    public void setup(){
        Assert.assertNotNull(pipeliteContext);
        Assert.assertNotNull(configuration);
    }

    @Test
    public void shouldRegisterFlow(){
        Assert.assertTrue(pipeliteContext.isRegistered("test-flow"));
    }

    @Test
    public void shouldInitializeContext(){
        Assert.assertTrue(configuration.isContextInitialized());
    }

    @EnablePipelite
    public static class TestConfiguration implements PipeliteContextInitializer {

        private boolean contextInitialized;

        @Override
        public void initializeContext(PipeliteContext context) {
            contextInitialized = true;
        }

        @Bean
        public FlowDefinition testFlowDefinition(){
            return Pipelite.defineFlow("test-flow")
                .fromSource("source")
                .toSink("sink")
                .build();
        }

        public boolean isContextInitialized(){
            return contextInitialized;
        }

    }

}
