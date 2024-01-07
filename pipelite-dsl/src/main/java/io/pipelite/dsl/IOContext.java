package io.pipelite.dsl;

import io.pipelite.dsl.route.RoutingSlip;

public interface IOContext extends Headers {

    Headers getHeaders();

    Class<?> getInputPayloadType();

    Object getInputPayload();

    <T> T getInputPayloadAs(Class<T> expectedType);

    void setOutputPayload(Object payload);

    void setReturnAddress(String flowName);

    void setRoutingSlip(RoutingSlip routingSlip);

}
