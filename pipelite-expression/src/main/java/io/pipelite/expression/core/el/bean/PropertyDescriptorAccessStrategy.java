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
package io.pipelite.expression.core.el.bean;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.ReflectionUtils;

public class PropertyDescriptorAccessStrategy implements BeanPropertyAccessStrategy {

    private static final String ACCESS_EXCEPTION_MESSAGE = "Cannot access to property %s on %s";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Object target;

    private final Map<String, PropertyDescriptor> propertyDescriptors;

    public PropertyDescriptorAccessStrategy(Object target) {
        Preconditions.notNull(target, "Target object is required");
        this.target = target;
        propertyDescriptors = new HashMap<String, PropertyDescriptor>();
    }

    @Override
    public void writeProperty(Object target, String propertyName, Object value) {
        Class<?> valueType = value.getClass();
        PropertyDescriptor propertyDescriptor = tryFindPropertyDescriptor(propertyName);
        if (propertyDescriptor != null) {
            if (propertyDescriptor.getPropertyType().isAssignableFrom(valueType)) {
                try {
                    Method setter = propertyDescriptor.getWriteMethod();
                    ReflectionUtils.invokeMethod(setter, target, value);
                }
                catch (RuntimeException cause) {
                    throw new BeanPropertyAccessException(
                            String.format(ACCESS_EXCEPTION_MESSAGE, propertyName, target.getClass()), cause);
                }
            }
            else {
                throw new ClassCastException(String.format("Unexpected value type %s", valueType));
            }
        }
        throw new BeanPropertyAccessException(String.format(ACCESS_EXCEPTION_MESSAGE, propertyName, target.getClass()));
    }

    @Override
    public <T> T readProperty(Object target, Class<T> propertyType, String propertyName) {
        PropertyDescriptor propertyDescriptor = tryFindPropertyDescriptor(propertyName);
        if (propertyDescriptor != null) {
            if (propertyType.isAssignableFrom(propertyDescriptor.getPropertyType())) {
                Method getter = propertyDescriptor.getReadMethod();
                try {
                    Object value = ReflectionUtils.invokeMethod(getter, target);
                    return propertyType.cast(value);
                }
                catch (RuntimeException cause) {
                    throw new BeanPropertyAccessException(
                            String.format(ACCESS_EXCEPTION_MESSAGE, propertyName, target.getClass()), cause);
                }
            }
            else {
                Throwable cause = new ClassCastException(
                        String.format("Cannot cast property value to %s", propertyType));
                throw new BeanPropertyAccessException(
                        String.format(ACCESS_EXCEPTION_MESSAGE, propertyName, target.getClass()), cause);
            }
        }
        throw new BeanPropertyAccessException(String.format(ACCESS_EXCEPTION_MESSAGE, propertyName, target.getClass()));
    }

    private PropertyDescriptor tryFindPropertyDescriptor(String propertyName) {
        if (logger.isTraceEnabled()) {
            logger.trace("Trying access {} on {} using PropertyGetter strategy", propertyName, target.getClass());
        }
        if (propertyDescriptors.containsKey(propertyName)) {
            return propertyDescriptors.get(propertyName);
        }
        else {
            try {
                PropertyDescriptor propertyDescriptor = ReflectionUtils.findProperty(target.getClass(), propertyName);
                propertyDescriptors.put(propertyName, propertyDescriptor);
                return propertyDescriptor;
            }
            catch (UndeclaredThrowableException exception) {
                if (logger.isTraceEnabled()) {
                    logger.trace("{} of class {} is not a valid JavaBean property", propertyName,
                            target.getClass().getSimpleName());
                }
                return null;
            }
        }
    }

}
