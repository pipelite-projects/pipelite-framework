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
package io.pipelite.dsl.headers;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/** Issue #171: what a flow declares about the headers that cross the border, as a value. */
public class HeaderPropagationTest {

    @Test
    public void givenNothingDeclared_thenNoHeaderCrosses() {
        Assert.assertTrue(HeaderPropagation.none().isNone());
        Assert.assertFalse(HeaderPropagation.none().isAll());
        Assert.assertTrue(new Configurer().headerPropagation().isNone());
    }

    @Test
    public void givenFilter_thenItHoldsTheNamesAsWrittenWithoutRepetitionsOfTheCase() {
        final HeaderPropagation propagation = HeaderPropagation.from(h -> h.filter("Idempotency-Key", "X-Tenant-Id", "idempotency-key"));

        Assert.assertFalse(propagation.isAll());
        Assert.assertFalse(propagation.isNone());
        Assert.assertEquals(List.of("Idempotency-Key", "X-Tenant-Id"), propagation.getNames());
    }

    @Test
    public void givenAll_thenEveryHeaderCrosses() {
        final HeaderPropagation propagation = HeaderPropagation.from(HeaderPropagationOperations::all);

        Assert.assertTrue(propagation.isAll());
        Assert.assertFalse(propagation.isNone());
        Assert.assertTrue(propagation.getNames().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenFilterWithoutNames_thenItIsRejected() {
        HeaderPropagation.from(HeaderPropagationOperations::filter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenABlankName_thenItIsRejected() {
        HeaderPropagation.from(h -> h.filter("Idempotency-Key", " "));
    }

    @Test
    public void givenAConfigurerWithTheCapability_thenItKeepsWhatWasDeclared() {
        final Configurer configurer = new Configurer();

        configurer.propagateHeaders(h -> h.filter("X-Tenant-Id"));

        Assert.assertEquals(List.of("X-Tenant-Id"), configurer.headerPropagation().getNames());
    }

    /** What a concrete configurer does: keeps the value in a field of its own, the rest is the default method. */
    private static final class Configurer implements PropagatesHeaders {

        private HeaderPropagation propagation = HeaderPropagation.none();

        @Override
        public HeaderPropagation headerPropagation() {
            return propagation;
        }

        @Override
        public void setHeaderPropagation(HeaderPropagation propagation) {
            this.propagation = propagation;
        }
    }
}
