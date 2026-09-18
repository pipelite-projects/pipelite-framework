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
package io.pipelite.dsl.route;

import io.pipelite.common.support.Preconditions;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.regex.Pattern;

/**
 * The itinerary of an exchange (Routing Slip EIP): an ordered list of <em>internal source
 * endpoints</em> - the {@code fromSource(...)} resource of each flow to visit, exactly the value
 * {@code Pipelite.defineFlow(...).fromSource("...")} was declared with. When a flow finishes and
 * the exchange carries a slip with routes left, the exchange hops to the next one instead of
 * taking the flow's own exit ({@code toSink}, {@code toRoute}, return address); the slip is
 * followed again at the end of that flow, and so on until it is exhausted, at which point the
 * last flow exits normally.
 * <p>
 * Only internal sources are accepted, never a channel adapter URL such as {@code kafka://...}:
 * a channel adapter is a terminal producer, nothing would carry the slip any further after
 * delivering there. To end an itinerary on an external system, let the last flow declare a
 * {@code toSink(...)} for it.
 * <p>
 * Serializable because the exchange carries it as a property and a durable inbox entry or a
 * retry dump serializes the whole exchange. Consumed as it is followed: routes already visited
 * are gone, so a resumed exchange continues from where it stopped.
 * <p>
 * Not part of the public API for now: {@code IOContext#setRoutingSlip} is disabled (issue #86), so
 * a processor cannot attach one; only {@code Exchange#setRoutingSlip} still can. What is described
 * here is implemented and tested, and is what the feature will look like when it is enabled again.
 */
public class RoutingSlip implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern PROTOCOL_QUALIFIED = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$");

    private final LinkedList<String> routes;

    public static RoutingSlip create(String... routes){
        Preconditions.state(routes != null, "Provide at least one route!");
        return new RoutingSlip(Arrays.asList(routes));
    }

    public RoutingSlip(Collection<String> routes){
        Preconditions.state(routes != null && !routes.isEmpty(), "Provide at least one route!");
        routes.forEach(RoutingSlip::validate);
        this.routes = new LinkedList<>(routes);
    }

    private static void validate(String route) {
        Preconditions.state(route != null && !route.trim().isEmpty(), "A route cannot be null or blank");
        Preconditions.state(!PROTOCOL_QUALIFIED.matcher(route).matches(), String.format(
            "Route '%s' is a channel adapter URL - a routing slip only accepts the fromSource(...) resource " +
                "of an internal flow. End the itinerary with a flow that declares toSink('%s') instead", route, route));
    }

    /**
     * Removes and returns the next route, or {@code null} if the slip is exhausted.
     */
    public String nextRoute(){
        return routes.poll();
    }

    public boolean hasNext(){
        return !routes.isEmpty();
    }

    /**
     * Puts back, at the front, a route just taken with {@link #nextRoute()} that could not be
     * delivered - so a retry of that hop finds it again instead of silently skipping it. Meant
     * for the framework's router, not for user code.
     */
    public void restoreRoute(String route){
        validate(route);
        routes.addFirst(route);
    }

}
