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
package io.pipelite.spi.endpoint;

import java.util.HashMap;
import java.util.Map;

public class EndpointProperties extends HashMap<String, String> {

    public EndpointProperties(){
    }

    public EndpointProperties(Map<String, String> properties){
        super(properties);
    }

    public Integer getAsInteger(String key){
        final String valueAsText = get(key);
        return valueAsText != null ? Integer.parseInt(valueAsText) : null;
    }

    public Integer getAsIntegerOrDefault(String key, Integer defaultValue){
        final String valueAsText = get(key);
        return valueAsText != null ? Integer.parseInt(valueAsText) : defaultValue;
    }

    public Long getAsLong(String key){
        final String valueAsText = get(key);
        return valueAsText != null ? Long.parseLong(valueAsText) : null;
    }

    public Long getAsLongOrDefault(String key, Long defaultValue){
        final String valueAsText = get(key);
        return valueAsText != null ? Long.parseLong(valueAsText) : defaultValue;
    }

}
