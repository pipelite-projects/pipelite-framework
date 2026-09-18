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
package io.pipelite.core.flow.route;

import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.common.support.serialization.ObjectToByteArrayConverter;
import io.pipelite.dsl.route.RoutingSlip;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Issue #86: a slip only names internal sources, and must survive the serialization a durable
 * inbox entry or a retry dump puts the whole exchange through.
 */
public class RoutingSlipTest {

    @Test
    public void givenChannelAdapterUrls_thenTheyAreRejectedNamingTheRoute() {
        for (String url : List.of("kafka://orders", "file://in", "http://x/y", "link://z")) {
            try {
                RoutingSlip.create("first", url);
                Assert.fail("expected " + url + " to be rejected");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains(url));
            }
        }
    }

    @Test
    public void givenNoRoutesOrABlankOne_thenItIsRejected() {
        for (String[] routes : new String[][]{{}, {""}, {"  "}, {"ok", null}}) {
            try {
                RoutingSlip.create(routes);
                Assert.fail("expected the slip to be rejected");
            } catch (IllegalStateException expected) {
                // expected
            }
        }
    }

    @Test
    public void givenInternalSources_thenRoutesAreTakenInOrderUntilExhausted() {
        final RoutingSlip slip = RoutingSlip.create("a-start", "b-start");
        Assert.assertEquals("a-start", slip.nextRoute());
        Assert.assertEquals("b-start", slip.nextRoute());
        Assert.assertFalse(slip.hasNext());
        Assert.assertNull(slip.nextRoute());
    }

    @Test
    public void givenARestoredRoute_thenItIsTakenAgainFirst() {
        final RoutingSlip slip = RoutingSlip.create("a-start", "b-start");
        final String taken = slip.nextRoute();
        slip.restoreRoute(taken);
        Assert.assertEquals("a-start", slip.nextRoute());
        Assert.assertEquals("b-start", slip.nextRoute());
    }

    @Test
    public void givenASlipPartiallyFollowed_thenTheRemainingRoutesSurviveSerialization() {
        final RoutingSlip slip = RoutingSlip.create("a-start", "b-start", "c-start");
        slip.nextRoute();

        final byte[] bytes = new ObjectToByteArrayConverter().convert(slip);
        final RoutingSlip restored = new ByteArrayToObjectConverter().convert(bytes, RoutingSlip.class);

        Assert.assertEquals("b-start", restored.nextRoute());
        Assert.assertEquals("c-start", restored.nextRoute());
        Assert.assertFalse(restored.hasNext());
    }

}
