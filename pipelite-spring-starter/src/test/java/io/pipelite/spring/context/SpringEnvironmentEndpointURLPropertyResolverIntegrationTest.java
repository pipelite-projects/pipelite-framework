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
package io.pipelite.spring.context;

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spring.EnablePipelite;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that {@code ${key}} placeholders in an endpoint URL are resolved via
 * {@link SpringEnvironmentEndpointURLPropertyResolver}, wired by {@link PipeliteAutoConfiguration},
 * during {@code PipeliteContext.start()} (triggered by {@link PipeliteContextLifecycleManager}
 * on {@code ContextRefreshedEvent}).
 */
public class SpringEnvironmentEndpointURLPropertyResolverIntegrationTest {

    @Test
    public void givenResolvablePlaceholder_whenContextRefreshes_thenSourceIsRegisteredUnderResolvedResourceName(){
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("test.source.name=source-resolved").applyTo(context);
            context.register(TestConfiguration.class);
            context.refresh();

            final PipeliteContext pipeliteContext = context.getBean(PipeliteContext.class);
            final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();

            // does not throw "Unrecognized destination": proves the flow is registered and routable
            // under the *resolved* resource name, not under the literal '${test.source.name}' placeholder.
            pipeliteContext.supplyExchange("source-resolved", exchangeFactory.createExchange("Hello Pipelite!"));
        }
    }

    @Test
    public void givenUnresolvablePlaceholder_whenContextRefreshes_thenStartupFailsWithIllegalArgumentException(){
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TestConfiguration.class);
            context.refresh();
            Assert.fail("Expected context refresh to fail because 'test.source.name' is unresolvable");
        } catch (Exception ex) {
            Throwable cause = ex;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            Assert.assertTrue(cause instanceof IllegalArgumentException);
        }
    }

    @EnablePipelite
    @Configuration
    public static class TestConfiguration {

        @Bean
        public FlowDefinition placeholderFlowDefinition(){
            return Pipelite.defineFlow("placeholder-flow")
                .fromSource("${test.source.name}")
                .toSink("sink")
                .build();
        }
    }

}
