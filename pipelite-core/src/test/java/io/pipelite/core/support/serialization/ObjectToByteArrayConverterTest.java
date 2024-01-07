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
