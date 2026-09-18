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
package io.pipelite.dsl;

// Disabled for the first stable release (issue #86): the Return Address and Routing Slip
// activation through the exchange is not settled yet and is likely to change, so it is not part
// of the public API. The implementation is kept, internal and unchanged: Exchange still has both
// methods (see Exchange#setReturnAddress/#setRoutingSlip), FlowFactory still wires the end-of-flow
// gate, RoutingSlip and the tests are all in place. Uncomment the two methods below (and the
// import), and the @Override on the Exchange side, to bring the feature back.
//
// import io.pipelite.dsl.route.RoutingSlip;

public interface IOContext extends Headers {

    Headers getHeaders();

    Class<?> getInputPayloadType();

    Object getInputPayload();

    <T> T getInputPayloadAs(Class<T> expectedType);

    void setOutputPayload(Object payload);

    // void setReturnAddress(String flowName);

    // /**
    //  * Attaches the itinerary of this exchange (Routing Slip EIP). At the end of this flow, and of
    //  * every flow the exchange then goes through, a slip with routes left takes priority over the
    //  * flow's own exit ({@code toSink}, {@code toRoute}, return address): the exchange hops to the
    //  * next route instead. Setting a slip therefore changes where this flow's exchange goes. It
    //  * replaces any slip already attached, so a step can re-plan the remaining route. Not followed
    //  * if a step fails or stops the execution. See {@link RoutingSlip} for what a route may be.
    //  */
    // void setRoutingSlip(RoutingSlip routingSlip);

}
