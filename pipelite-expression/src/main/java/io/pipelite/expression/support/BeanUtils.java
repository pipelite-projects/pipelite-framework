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
package io.pipelite.expression.support;

import io.pipelite.common.support.Preconditions;

import java.lang.reflect.Method;

public class BeanUtils {

    private BeanUtils() {
    }

    private static final String BEAN_CLASS_REQUIRED = "Bean class is required";

    public static boolean isProperty(Class<?> beanClass, String beanProperty) {
        Preconditions.notNull(beanClass, BEAN_CLASS_REQUIRED);
        Preconditions.notNull(beanProperty, BEAN_CLASS_REQUIRED);
        return ReflectionUtils.findGetter(beanClass, beanProperty) != null;
    }

    public static boolean isPropertyAssignableFrom(Class<?> beanClass, String beanProperty, Class<?> type) {
        Preconditions.notNull(beanClass, BEAN_CLASS_REQUIRED);
        Preconditions.notNull(beanProperty, BEAN_CLASS_REQUIRED);
        Method method = ReflectionUtils.findGetter(beanClass, beanProperty);
        if (method == null) {
            return false;
        }
        return type.isAssignableFrom(method.getReturnType());
    }

    public static boolean isPropertyOfSameType(Class<?> beanClass, String beanProperty, Class<?> type) {
        Preconditions.notNull(beanClass, BEAN_CLASS_REQUIRED);
        Preconditions.notNull(beanProperty, BEAN_CLASS_REQUIRED);
        Method method = ReflectionUtils.findGetter(beanClass, beanProperty);
        if (method == null) {
            return false;
        }
        return method.getReturnType().getClass().equals(type);
    }

}
