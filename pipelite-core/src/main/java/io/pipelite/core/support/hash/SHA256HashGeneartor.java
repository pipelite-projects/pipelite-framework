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
