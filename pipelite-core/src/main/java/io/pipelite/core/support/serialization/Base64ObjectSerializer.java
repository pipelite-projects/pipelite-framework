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
package io.pipelite.core.support.serialization;

public class Base64ObjectSerializer implements ObjectSerializer {

    private final ObjectToByteArrayConverter byteArrayConverter;

    public Base64ObjectSerializer() {
        this.byteArrayConverter = new ObjectToByteArrayConverter();
    }

    @Override
    public String getEncoding() {
        return "base64";
    }

    @Override
    public String serializeObject(Object input) {
        final byte[] content = byteArrayConverter.convert(input);
        return BaseEncoding.base64().encode(content);
    }
}