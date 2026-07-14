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
package io.pipelite.spi.channel;

import org.junit.Assert;
import org.junit.Test;

public class ChannelURLTest {

    @Test
    public void givenNoProtocol_whenParse_thenResourceIsWholeString(){
        final ChannelURL channelURL = ChannelURL.parse("source-01");
        Assert.assertFalse(channelURL.hasProtocol());
        Assert.assertNull(channelURL.getProtocol());
        Assert.assertEquals("source-01", channelURL.getEndpointURL());
    }

    @Test
    public void givenProtocolWithNonEmptyResource_whenParse_thenProtocolAndResourceAreSplit(){
        final ChannelURL channelURL = ChannelURL.parse("file:///data/in/orders.log");
        Assert.assertTrue(channelURL.hasProtocol());
        Assert.assertEquals("file", channelURL.getProtocol());
        Assert.assertEquals("/data/in/orders.log", channelURL.getEndpointURL());
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenProtocolWithEmptyResource_whenParse_thenThrowIllegalArgumentException(){
        ChannelURL.parse("http://");
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenEmptyUrl_whenParse_thenThrowIllegalArgumentException(){
        ChannelURL.parse("");
    }

    @Test
    public void givenMultipleSchemeOccurrences_whenParse_thenStopsAtFirstOccurrence(){
        final ChannelURL channelURL = ChannelURL.parse("a://b://c");
        Assert.assertTrue(channelURL.hasProtocol());
        Assert.assertEquals("a", channelURL.getProtocol());
        Assert.assertEquals("b://c", channelURL.getEndpointURL());
    }

}
