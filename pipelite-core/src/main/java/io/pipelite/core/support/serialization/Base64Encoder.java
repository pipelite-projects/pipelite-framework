package io.pipelite.core.support.serialization;

import java.util.Base64;

public class Base64Encoder implements BaseEncoder {

    @Override
    public String encode(byte[] byteArray) {
        return Base64.getEncoder()
            .encodeToString(byteArray);
    }

    @Override
    public byte[] decode(String text) {
        return Base64.getDecoder()
            .decode(text);
    }
}
