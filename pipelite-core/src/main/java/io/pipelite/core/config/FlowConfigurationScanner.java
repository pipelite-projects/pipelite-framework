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

import io.pipelite.dsl.annotation.DefineFlow;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Discovers and invokes {@code @DefineFlow} methods declared on a class annotated with
 * {@code @FlowConfiguration}, resolving each method's parameters against a
 * {@link DependencyRegistry}.
 */
public class FlowConfigurationScanner {

    /**
     * Instantiates {@code configurationClass} via its default constructor, then invokes all
     * {@code @DefineFlow} methods resolving parameters against {@code dependencyRegistry}.
     * Used by the native plain-Java SPI.
     */
    public List<FlowDefinition> scan(Class<?> configurationClass, DependencyRegistry dependencyRegistry) {

        if(!configurationClass.isAnnotationPresent(FlowConfiguration.class)){
            throw new FlowConfigurationException(String.format(
                "Class '%s' is not annotated with @FlowConfiguration", configurationClass.getName()));
        }

        return scan(instantiate(configurationClass), dependencyRegistry);
    }

    /**
     * Invokes all {@code @DefineFlow} methods on an already-constructed instance, resolving
     * parameters against {@code dependencyRegistry}. Used by the Spring integration layer where
     * Spring has already instantiated and injected the {@code @FlowConfiguration} bean.
     */
    public List<FlowDefinition> scan(Object configurationInstance, DependencyRegistry dependencyRegistry) {

        final Class<?> configurationClass = configurationInstance.getClass();
        if(!configurationClass.isAnnotationPresent(FlowConfiguration.class)){
            throw new FlowConfigurationException(String.format(
                "Class '%s' is not annotated with @FlowConfiguration", configurationClass.getName()));
        }

        final List<FlowDefinition> flowDefinitions = new ArrayList<>();
        for(Method method : configurationClass.getDeclaredMethods()){
            if(method.isAnnotationPresent(DefineFlow.class)){
                flowDefinitions.add(invokeDefineFlowMethod(configurationInstance, method, dependencyRegistry));
            }
        }
        return flowDefinitions;
    }

    private Object instantiate(Class<?> configurationClass) {
        try{
            final var constructor = configurationClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception){
            throw new FlowConfigurationException(String.format(
                "Unable to instantiate @FlowConfiguration class '%s' via its default constructor",
                configurationClass.getName()), exception);
        }
    }

    private FlowDefinition invokeDefineFlowMethod(Object configurationInstance, Method method, DependencyRegistry dependencyRegistry) {

        if(!FlowDefinition.class.isAssignableFrom(method.getReturnType())){
            throw new FlowConfigurationException(String.format(
                "@DefineFlow method '%s.%s' must return a FlowDefinition",
                method.getDeclaringClass().getName(), method.getName()));
        }

        final Parameter[] parameters = method.getParameters();
        final Object[] arguments = new Object[parameters.length];
        for(int i = 0; i < parameters.length; i++){
            final Class<?> parameterType = parameters[i].getType();
            final Optional<Object> resolved = dependencyRegistry.resolve(parameterType);
            arguments[i] = resolved.orElseThrow(() -> new UnresolvableDependencyException(method, parameterType));
        }

        try{
            method.setAccessible(true);
            return (FlowDefinition) method.invoke(configurationInstance, arguments);
        } catch (InvocationTargetException exception){
            throw new FlowConfigurationException(String.format(
                "@DefineFlow method '%s.%s' threw an exception",
                method.getDeclaringClass().getName(), method.getName()), exception.getTargetException());
        } catch (IllegalAccessException exception){
            throw new FlowConfigurationException(String.format(
                "Unable to invoke @DefineFlow method '%s.%s'",
                method.getDeclaringClass().getName(), method.getName()), exception);
        }
    }

}
