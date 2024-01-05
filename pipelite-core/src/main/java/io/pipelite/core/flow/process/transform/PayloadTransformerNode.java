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
package io.pipelite.core.flow.process.transform;

import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.PayloadHolder;
import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;

import java.util.Arrays;
import java.util.Collection;

public class PayloadTransformerNode implements Processor {

    private final Collection<Class<?>> WRAPPER_TYPES = Arrays.asList(new Class<?>[]{
        String.class, Integer.class, Byte.class, Character.class, Boolean.class, Double.class, Float.class, Long.class, Short.class
    });

    private final PayloadTransformer payloadTransformer;

    public PayloadTransformerNode(PayloadTransformer payloadTransformer) {
        this.payloadTransformer = payloadTransformer;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {
        final Object outputPayload = payloadTransformer.transform(new PayloadHolder(ioContext.getInputPayload()));
        ioContext.setOutputPayload(outputPayload);
    }

    @SuppressWarnings("unused")
    private boolean isWrapperType(Class<?> payloadType){
        return WRAPPER_TYPES
            .stream()
            .anyMatch(wrapperType ->
                wrapperType.isAssignableFrom(payloadType));
    }
}
