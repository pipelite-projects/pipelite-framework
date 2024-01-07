package io.pipelite.core.support.hash;

import io.pipelite.core.support.serialization.BaseEncoding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA256HashGeneartor implements HashGenerator{

    private static final String ALGORITHM = "SHA-256";

    private final MessageDigest messageDigest;

    public SHA256HashGeneartor() {
        try {
            this.messageDigest = MessageDigest.getInstance(ALGORITHM);
        }catch(NoSuchAlgorithmException exception){
            throw new RuntimeException(String.format("Unrecognized algorithm %s", ALGORITHM), exception);
        }
    }

    @Override
    public String hash(byte[] content) {
        final byte[] hashAsByteArray = messageDigest.digest(content);
        return BaseEncoding.base16().encode(hashAsByteArray);
    }

}
