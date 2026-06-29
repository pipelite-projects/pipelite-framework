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

import io.pipelite.spring.EnablePipelite;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * Reads the {@code flowConfigurations} attribute from {@code @EnablePipelite} and registers a
 * bean definition for each declared class. Spring will construct these beans with standard
 * constructor injection, making them available for {@link FlowConfigurationBeanPostProcessor}
 * to intercept in phase 2 of the bootstrap.
 *
 * <p>This runs in phase 1 of the Spring bootstrap (bean definition registration) and has no
 * dependency on {@code PipeliteContext}, which does not exist yet at this point.
 */
public class FlowConfigurationRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        final Map<String, Object> attributes =
            metadata.getAnnotationAttributes(EnablePipelite.class.getName());
        if (attributes == null) {
            return;
        }
        final Class<?>[] flowConfigurations = (Class<?>[]) attributes.get("flowConfigurations");
        if (flowConfigurations == null) {
            return;
        }
        for (Class<?> configurationClass : flowConfigurations) {
            final String beanName = configurationClass.getName();
            if (!registry.containsBeanDefinition(beanName)) {
                registry.registerBeanDefinition(beanName, new RootBeanDefinition(configurationClass));
            }
        }
    }
}
