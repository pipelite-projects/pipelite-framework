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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ByteArrayToObjectConverter {

    public <T> T convert(byte[] content, Class<T> expectedType){

        ObjectInputStream ois = null;
        try{
            final ByteArrayInputStream bais = new ByteArrayInputStream(content);
            ois = new ObjectInputStream(bais);
            final Object object = ois.readObject();
            if(object == null){
                return null;
            } else {
                if(expectedType.isAssignableFrom(object.getClass())){
                    return expectedType.cast(object);
                }
                throw new ClassCastException(String.format("Unable to cast from %s to %s", object.getClass(), expectedType));
            }
        } catch (IOException | ClassNotFoundException exception){
            throw new ByteArrayReadException("An underlying error occurred reading serialized content", exception);
        } finally {
            try {
                if(ois != null){
                    ois.close();
                }
            }catch (IOException ignore){}
        }

    }
}
