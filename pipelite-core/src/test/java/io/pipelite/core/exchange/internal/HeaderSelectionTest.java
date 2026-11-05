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
package io.pipelite.core.exchange.internal;

import io.pipelite.dsl.headers.HeaderPropagation;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Issue #171: the one place where the headers that cross the border between a flow and the outside are chosen. */
public class HeaderSelectionTest {

    private static HeaderSelection filter(String... names) {
        return new HeaderSelection(HeaderPropagation.ofNames(names));
    }

    private static Map<String, Object> headers(Object... namesAndValues) {
        final Map<String, Object> headers = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            headers.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return headers;
    }

    @Test
    public void givenNothingDeclared_thenNothingCrosses() {
        final Map<String, Object> given = headers("Authorization", "Bearer secret", "Idempotency-Key", "abc");

        Assert.assertTrue(HeaderSelection.none().select(given).isEmpty());
        Assert.assertTrue(new HeaderSelection(HeaderPropagation.none()).select(given).isEmpty());
    }

    @Test
    public void givenFilter_thenOnlyTheNamedHeadersCross() {
        final Map<String, Object> given = headers("Authorization", "Bearer secret", "Idempotency-Key", "abc", "X-Tenant-Id", "t-1");

        final Map<String, String> selected = filter("Idempotency-Key", "X-Tenant-Id").select(given);

        Assert.assertEquals(Map.of("Idempotency-Key", "abc", "X-Tenant-Id", "t-1"), selected);
    }

    @Test
    public void givenFilter_thenTheCaseIsIgnoredAndTheHeaderHasTheNameTheFlowWrote() {
        final Map<String, Object> given = headers("idempotency-key", "abc");

        final Map<String, String> selected = filter("Idempotency-Key").select(given);

        Assert.assertEquals(Map.of("Idempotency-Key", "abc"), selected);
    }

    @Test
    public void givenFilter_whenAHeaderIsMissing_thenItIsSimplyNotThere() {
        Assert.assertTrue(filter("Idempotency-Key").select(headers("Other", "x")).isEmpty());
        Assert.assertTrue(filter("Idempotency-Key").select(Map.of()).isEmpty());
        Assert.assertTrue(filter("Idempotency-Key").select(null).isEmpty());
    }

    @Test
    public void givenAnExactMatchAndAnotherCase_thenTheExactOneWins() {
        final Map<String, Object> given = headers("x-id", "lower", "X-Id", "exact");

        Assert.assertEquals(Map.of("X-Id", "exact"), filter("X-Id").select(given));
    }

    @Test
    public void givenAll_thenEveryHeaderCrossesWithItsOwnName() {
        final Map<String, Object> given = headers("Authorization", "Bearer secret", "idempotency-key", "abc");

        final Map<String, String> selected = new HeaderSelection(HeaderPropagation.from(h -> h.all())).select(given);

        Assert.assertEquals(given, selected);
    }

    @Test
    public void givenValuesThatAreNotText_thenSimpleOnesAreConvertedAndTheOthersAreLeftBehind() {
        final Map<String, Object> given = headers(
            "count", 3, "flag", true, "unit", TimeUnit.SECONDS, "when", new Object(), "nothing", null);

        final Map<String, String> selected = new HeaderSelection(HeaderPropagation.from(h -> h.all())).select(given);

        Assert.assertEquals(Map.of("count", "3", "flag", "true", "unit", "SECONDS"), selected);
    }
}
