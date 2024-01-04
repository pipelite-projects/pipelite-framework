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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

public class ObjectToByteArrayConverterTest {

    private ObjectToByteArrayConverter subject;

    @Before
    public void setup(){
        subject = new ObjectToByteArrayConverter();
    }

    @Test
    public void shouldConvertObject(){

        final TestObject object = new TestObject("value");
        final byte[] serializedObject = subject.convert(object);

        Assert.assertNotNull(serializedObject);

        //final String objectAsHexString = BaseEncoding.base16().encode(serializedObject);
        //final String objectAsBase64String = BaseEncoding.base64().encode(serializedObject);
        //Assert.assertNotNull(objectAsHexString);
        //Assert.assertNotNull(objectAsBase64String);

    }

    public static class TestObject implements Serializable {

        private final String attribute;

        public TestObject(String attribute) {
            this.attribute = attribute;
        }

    }

}
