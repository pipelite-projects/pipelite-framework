package io.pipelite.dsl.process;

import java.util.Objects;

public class PayloadHolder {

    private final Object payload;

    public PayloadHolder(Object payload) {
        Objects.requireNonNull(payload, "payload is required and cannot be null");
        this.payload = payload;
    }

    public <T> T getPayloadAs(Class<T> expectedType){
        if(expectedType.isAssignableFrom(payload.getClass())){
            return expectedType.cast(payload);
        }
        throw new ClassCastException(String.format("Cannot cast from %s to expectedType %s", payload.getClass(), expectedType));
    }

    @Override
    public String toString(){
        return payload.toString();
    }
}
