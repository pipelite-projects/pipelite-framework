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

import java.nio.charset.StandardCharsets;

public class Base16EncoderTest {

    private BaseEncoder subject;

    @Before
    public void setup(){
        subject = BaseEncoding.base16();
    }

    @Test
    public void shouldEncodeCorrectly(){

        final String expectedText = "Hello Pipelite!";
        final String expectedEncodedText = "48656c6c6f20506970656c69746521";
        final String actualEncodedText = subject.encode(expectedText.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(expectedEncodedText, actualEncodedText);

    }

    @Test
    public void shouldDecodeCorrectly(){

        final String encodedText = "48656c6c6f20506970656c69746521";
        final byte[] actualDecodedBytes = subject.decode(encodedText);

        Assert.assertEquals("Hello Pipelite!", new String(actualDecodedBytes));

    }
}
