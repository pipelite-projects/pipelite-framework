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

import io.pipelite.core.context.PipeliteContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.Assert;

import java.util.Objects;

public class PipeliteContextInitializerBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private final PipeliteContext context;

    public PipeliteContextInitializerBeanPostProcessor(PipeliteContext context){
        Assert.notNull(context, "PipeliteContext is required and cannot be null");
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

        if(bean instanceof PipeliteContextInitializer){
            ((PipeliteContextInitializer)bean).initializeContext(context);
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
