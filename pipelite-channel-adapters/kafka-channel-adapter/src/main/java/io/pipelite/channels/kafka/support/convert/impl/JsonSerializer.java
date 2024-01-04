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
package io.pipelite.channels.kafka.support.convert.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

public class JsonSerializer implements Serializer<Object> {

    private final ObjectMapper jacksonMapper;

    public JsonSerializer(){
        this(null);
    }

    public JsonSerializer(ObjectMapper jacksonMapper){
        this.jacksonMapper = jacksonMapper != null ? jacksonMapper : new ObjectMapper();
    }

    @Override
    public byte[] serialize(String topic, Object data) {
        try {
            return jacksonMapper.writeValueAsBytes(data);
        } catch (JsonProcessingException jsonException) {
            throw new JsonSerializationException("An underlying error occurred serializing data", jsonException);
        }
    }

    @Override
    public byte[] serialize(String topic, Headers headers, Object data) {
        return Serializer.super.serialize(topic, headers, data);
    }
}
