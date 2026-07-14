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

import io.pipelite.core.context.ConfigurablePipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;

@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class PipeliteAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public ConfigurablePipeliteContext pipeliteContext(Environment environment){
        ConfigurablePipeliteContext context = new DefaultPipeliteContext();
        context.setEndpointURLPropertyResolver(new SpringEnvironmentEndpointURLPropertyResolver(environment));
        return context;
    }

    @Bean
    public PipeliteContextLifecycleManager pipeliteContextLifecycleManager(PipeliteContext context){
        return new PipeliteContextLifecycleManager(context);
    }

    @Bean
    public FlowDefinitionRegistrar flowDefinitionRegistrar(PipeliteContext pipeliteContext){
        return new FlowDefinitionRegistrar(pipeliteContext);
    }

    @Bean
    public PipeliteContextInitializerBeanPostProcessor pipeliteContextInitializerBeanPostProcessor(PipeliteContext pipeliteContext){
        return new PipeliteContextInitializerBeanPostProcessor(pipeliteContext);
    }

    @Bean
    public FlowConfigurationBeanPostProcessor flowConfigurationBeanPostProcessor(PipeliteContext pipeliteContext){
        return new FlowConfigurationBeanPostProcessor(pipeliteContext);
    }

}
