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

import java.util.ArrayList;
import java.util.Collection;

/**
 * This implementation is thread-safe
 * 
 * @author Vincenzo Autiero
 *
 */
public class CompositeElResolver implements ElResolver {

    private static final Object MUTEX = new Object();

    private Collection<ElResolver> elResolvers;

    public CompositeElResolver() {
        elResolvers = new ArrayList<ElResolver>();
    }

    public void add(ElResolver resolver) {
        elResolvers.add(resolver);
    }

    @Override
    public Object resolveValue(ElContext context, Object target, Object property) {
        synchronized (MUTEX) {
            for (ElResolver resolver : elResolvers) {
                Object value = resolver.resolveValue(context, target, property);
                if (context.isPropertyResolved()) {
                    return value;
                }
            }
            return null;
        }
    }

}
