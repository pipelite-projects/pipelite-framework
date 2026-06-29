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

import io.pipelite.core.config.AmbiguousDependencyException;
import io.pipelite.core.config.DependencyRegistry;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * {@link DependencyRegistry} adapter that delegates lookups to a Spring {@link ApplicationContext}.
 * {@link #register(String, Object)} is not supported — Spring is the authoritative source of bean instances.
 */
public class SpringDependencyRegistry implements DependencyRegistry {

    private final ApplicationContext applicationContext;

    public SpringDependencyRegistry(ApplicationContext applicationContext) {
        Assert.notNull(applicationContext, "applicationContext is required and cannot be null");
        this.applicationContext = applicationContext;
    }

    @Override
    public void register(String name, Object instance) {
        throw new UnsupportedOperationException(
            "SpringDependencyRegistry is read-only: bean instances are managed by Spring, not registered manually");
    }

    @Override
    public Optional<Object> resolve(Class<?> type) {
        try {
            return Optional.of(applicationContext.getBean(type));
        } catch (NoUniqueBeanDefinitionException ex) {
            throw new AmbiguousDependencyException(type, ex.getBeanNamesFound());
        } catch (NoSuchBeanDefinitionException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Object> resolve(String name) {
        try {
            return Optional.of(applicationContext.getBean(name));
        } catch (NoSuchBeanDefinitionException ex) {
            return Optional.empty();
        }
    }
}
