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
package io.pipelite.spi.flow;

import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.flow.exchange.Exchange;

import java.util.Objects;

public class Flow {

    private final String name;
    private final String endpointURI;
    //private final Endpoint sourceEndpoint;
    private final Consumer consumer;

    public Flow(String name, String endpointURI, Endpoint sourceEndpoint, Consumer consumer) {
        this(name, endpointURI, consumer);
    }

    public Flow(String name, String endpointURI, Consumer consumer){
        this.name = name;
        this.endpointURI = endpointURI;
        //this.sourceEndpoint = sourceEndpoint;
        this.consumer = consumer;
    }

    public String getName() {
        return name;
    }

    public void supply(Exchange exchange){
        consumer.consume(exchange);
    }

    public Consumer getConsumer(){
        return getConsumerAs(Consumer.class);
    }

    public <T> T getConsumerAs(Class<T> expectedType){
        Objects.requireNonNull(expectedType, "expectedType is required and cannot be null");
        if(consumer == null){
            return null;
        } else if(isConsumerOfType(expectedType)){
            return expectedType.cast(consumer);
        }
        throw new ClassCastException(String.format("Unable to cast from '%s' to '%s",
            consumer.getClass(), expectedType));
    }

    public boolean isConsumerOfType(Class<?> expectedType){
        Objects.requireNonNull(expectedType, "expectedType is required and cannot be null");
        return expectedType.isAssignableFrom(consumer.getClass());
    }

    public String getEndpointURI(){
        return endpointURI;
    }

}
