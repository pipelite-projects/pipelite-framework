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
package io.pipelite.spi.flow.exchange;

import io.pipelite.dsl.Headers;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.route.RoutingSlip;
import io.pipelite.spi.context.IOKeys;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Exchange implements IOContext, Serializable {

    private final Headers headers;
    private final Map<String, Object> properties;
    private final Message input;
    private Message output;

    public Exchange(Message input) {
        this(input, null);
    }

    public Exchange(Message input, Headers headers) {
        this(input, null, headers, null);
    }

    public Exchange(Message input, Message output, Headers headers) {
        this(input, output, headers, null);
    }

    public Exchange(Message input, Message output, Headers headers, Map<String, Object> properties) {
        Objects.requireNonNull(input, "input is required and cannot be null.");
        this.input = input;
        this.output = output;
        this.headers = headers != null ? headers : new HeadersImpl();
        this.properties = properties != null ? properties : new ConcurrentHashMap<>();
    }

    @Override
    public Optional<String> tryGetHeader(String headerName) {
        return headers.tryGetHeader(headerName);
    }

    @Override
    public String expectHeader(String headerName) {
        return headers.expectHeader(headerName);
    }

    @Override
    public <T> Optional<T> tryGetHeaderAs(String headerName, Class<T> expectedType) {
        return headers.tryGetHeaderAs(headerName, expectedType);
    }

    @Override
    public void putHeader(String headerName, Object headerValue) {
        headers.putHeader(headerName, headerValue);
    }

    public void removeHeader(String headerName){
        headers.removeHeader(headerName);
    }

    public Set<Map.Entry<String, Object>> propertySet(){
        return properties.entrySet();
    }

    public <P> P getPropertyOrDefault(String propertyName, Class<P> expectedType, P defaultValue) {
        final P value = getProperty(propertyName, expectedType);
        return value != null ? value : defaultValue;
    }

    public <P> P getProperty(String propertyName, Class<P> expectedType) {
        if(properties.containsKey(propertyName)){
            final Object propertyValue = properties.get(propertyName);
            if(propertyValue != null){
                if(expectedType.isAssignableFrom(propertyValue.getClass())){
                    return expectedType.cast(propertyValue);
                }
                throw new ClassCastException(String.format("Cannot convert value from %s to expectedType %s", propertyValue.getClass(), expectedType));
            }
        }
        return null;
    }

    public void setProperty(String propertyName, Object propertyValue) {
        properties.put(propertyName, propertyValue);
    }

    @SuppressWarnings("unchecked")
    public <P> P removeProperty(String propertyName) {
        return (P) properties.remove(propertyName);
    }

    @Override
    public Headers getHeaders(){
        return headers;
    }

    @Override
    public Iterable<String> headersKeySet() {
        return headers.headersKeySet();
    }

    @Override
    public boolean hasHeader(String headerName) {
        return headers.hasHeader(headerName);
    }

    public Message getInput(){
        return input;
    }

    public void setInputPayload(Object inputPayload){
        input.setPayload(inputPayload);
    }

    public boolean isOutputSet(){
        return output != null;
    }

    public void setOutput(Message output){
        this.output = output;
    }

    @Override
    public Class<?> getInputPayloadType() {
        return input.getPayloadType();
    }

    @Override
    public Object getInputPayload() {
        return input.getPayloadAs(Object.class);
    }

    @Override
    public <T> T getInputPayloadAs(Class<T> expectedType) {
        return input.getPayloadAs(expectedType);
    }

    @Override
    public void setOutputPayload(Object payload) {
        Objects.requireNonNull(output, "Unable to set output payload, output message is null");
        output.setPayload(payload);
    }

    public Message getOutput(){
        return output;
    }

    public void forwardIfNecessary() {
        if(input.hasPayload() && !output.hasPayload()){
            output.setPayload(input.getPayloadAs(Object.class));
        }
    }

    public Optional<String> tryGetReturnAddress(){
        return headers.tryGetHeader(IOKeys.RETURN_ADDRESS_HEADER_NAME);
    }

    @Override
    public void setReturnAddress(String flowName) {
        if(!headers.hasHeader(IOKeys.RETURN_ADDRESS_HEADER_NAME)){
            headers.putHeader(IOKeys.RETURN_ADDRESS_HEADER_NAME, flowName);
        }
    }


    public void resetReturnAddress(){
        headers.removeHeader(IOKeys.RETURN_ADDRESS_HEADER_NAME);
    }

    @Override
    public void setRoutingSlip(RoutingSlip routingSlip) {
        properties.put(IOKeys.ROUTING_SLIP_PROPERTY_NAME, routingSlip);
    }

    public Optional<RoutingSlip> tryGetRoutingSlip(){
        return Optional.ofNullable(getPropertyOrDefault(IOKeys.ROUTING_SLIP_PROPERTY_NAME, RoutingSlip.class, null));
    }

}
