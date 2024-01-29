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
package io.pipelite.core.definition.builder;

import java.lang.reflect.Constructor;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Builder<T> {

    private final Class<T> targetType;
    private final Collection<Consumer<T>> propertySetters;
    private Object[] constructorParameters;

    public static <T> Builder<T> forType(Class<T> targetType) {
        return new Builder<>(targetType);
    }

    private Builder(Class<T> targetType) {
        Objects.requireNonNull(targetType, "targetType is required and cannot be null");
        this.targetType = targetType;
        this.propertySetters = new ArrayList<>();
    }

    public Builder<T> constructWith(Object... constructorParameters) {
        this.constructorParameters = constructorParameters;
        return this;
    }

    public Builder<T> with(Consumer<T> setter) {
        propertySetters.add(setter);
        return this;
    }

    public T build() {
        try{
            final T target = newInstance();
            propertySetters.forEach(propertySetter -> propertySetter.accept(target));
            return target;
        }catch (Exception exception){
            rethrowRuntimeException(exception);
            return null;
        }
    }

    private T newInstance() throws Exception {
        Constructor<T> constructor;
        if (constructorParameters != null) {
            final Class<?>[] parametersType = Arrays.stream(constructorParameters)
                .map(Object::getClass)
                .toList()
                .toArray(new Class[]{});
            constructor = targetType.getConstructor(parametersType);
            constructor.setAccessible(true);
            return constructor.newInstance(constructorParameters);
        } else {
            constructor = targetType.getConstructor();
            return constructor.newInstance();
        }
    }

    private static void rethrowRuntimeException(Throwable ex) {
        if (ex instanceof RuntimeException) {
            throw (RuntimeException)ex;
        } else if (ex instanceof Error) {
            throw (Error)ex;
        } else {
            throw new UndeclaredThrowableException(ex);
        }
    }

}
