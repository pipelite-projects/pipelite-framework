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
