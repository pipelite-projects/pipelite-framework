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
package io.pipelite.dsl.process;

import java.util.Objects;

public class PayloadHolder {

    private final Object payload;

    public PayloadHolder(Object payload) {
        Objects.requireNonNull(payload, "payload is required and cannot be null");
        this.payload = payload;
    }

    public <T> T getPayloadAs(Class<T> expectedType){
        if(expectedType.isAssignableFrom(payload.getClass())){
            return expectedType.cast(payload);
        }
        throw new ClassCastException(String.format("Cannot cast from %s to expectedType %s", payload.getClass(), expectedType));
    }

    @Override
    public String toString(){
        return payload.toString();
    }
}
