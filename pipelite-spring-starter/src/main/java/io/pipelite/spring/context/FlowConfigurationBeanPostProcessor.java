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

import io.pipelite.core.config.FlowConfigurationScanner;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Intercepts any Spring bean whose class is annotated with {@code @FlowConfiguration} —
 * regardless of how the bean was registered (via {@code @EnablePipelite(flowConfigurations = ...)},
 * component scan, or explicit {@code @Bean}) — and registers all {@code @DefineFlow} methods'
 * results in the {@link PipeliteContext}.
 *
 * <p>Parameter resolution delegates to a {@link SpringDependencyRegistry} backed by the
 * {@link ApplicationContext}, so {@code @DefineFlow} method parameters are satisfied by standard
 * Spring beans.
 */
public class FlowConfigurationBeanPostProcessor implements BeanPostProcessor, PriorityOrdered, ApplicationContextAware {

    private final FlowConfigurationScanner scanner = new FlowConfigurationScanner();
    private final PipeliteContext pipeliteContext;
    private SpringDependencyRegistry springDependencyRegistry;

    public FlowConfigurationBeanPostProcessor(PipeliteContext pipeliteContext) {
        Assert.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.pipeliteContext = pipeliteContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.springDependencyRegistry = new SpringDependencyRegistry(applicationContext);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().isAnnotationPresent(FlowConfiguration.class)) {
            final List<FlowDefinition> flowDefinitions = scanner.scan(bean, springDependencyRegistry);
            for (FlowDefinition flowDefinition : flowDefinitions) {
                pipeliteContext.registerFlowDefinition(flowDefinition);
            }
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
