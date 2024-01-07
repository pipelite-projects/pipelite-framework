package io.pipelite.core.support.serialization;

public class BaseEncoding {

    private BaseEncoding(){}

    public static BaseEncoder base16(){
        return new Base16Encoder();
    }

    public static BaseEncoder base64(){
        return new Base64Encoder();
    }

}
