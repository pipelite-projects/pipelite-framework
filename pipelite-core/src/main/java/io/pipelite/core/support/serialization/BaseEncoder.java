package io.pipelite.core.support.serialization;

public interface BaseEncoder {

    String encode(byte[] byteArray);
    byte[] decode(String text);

}
