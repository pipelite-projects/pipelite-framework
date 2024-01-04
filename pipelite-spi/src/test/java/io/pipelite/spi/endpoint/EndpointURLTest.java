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

import org.junit.Assert;
import org.junit.Test;

public class EndpointURLTest {

    @Test
    public void shouldParseEndpointURL(){

        final EndpointURL endpointURL = EndpointURL.parse("source-01?param1=1&param2=textValue");
        Assert.assertNotNull(endpointURL);

        final EndpointProperties props = endpointURL.getProperties();
        Assert.assertEquals((Integer)1, props.getAsInteger("param1"));
        Assert.assertEquals("textValue", props.get("param2"));

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenInadmissibleCharacters01_whenParseURL_thenThrowIllegalArgumentException(){
        EndpointURL.parse("SOURCE-01");
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenInadmissibleCharacters02_whenParseURL_thenThrowIllegalArgumentException(){
        EndpointURL.parse("source@01");
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenProtocolPrefix_whenParseURL_thenThrowIllegalArgumentException(){
        EndpointURL.parse("link://source01");
    }

}
