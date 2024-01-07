package io.pipelite.dsl.route;

import java.util.Objects;

public class PayloadTypeCondition implements Condition {

    private final Class<?> expectedPayloadType;

    public PayloadTypeCondition(Class<?> expectedPayloadType) {
        Objects.requireNonNull(expectedPayloadType, "expectedPayloadType is required and cannot be null");
        this.expectedPayloadType = expectedPayloadType;
    }

    public Class<?> getExpectedPayloadType() {
        return expectedPayloadType;
    }
}
