package io.pipelite.components.http.config;

import java.util.Arrays;

public enum RequestMethod {

    POST, PUT;

    public static RequestMethod fromValue(String requestMethod){
        for (RequestMethod value : values()){
            if(value.name().equals(requestMethod.toUpperCase())){
                return value;
            }
        }
        throw new IllegalArgumentException(String.format("Illegal value provided '%s'", requestMethod));
    }

    public static boolean isAllowed(String requestMethod){
        return Arrays.stream(values())
            .anyMatch(value -> value.name().equals(requestMethod));
    }
}
