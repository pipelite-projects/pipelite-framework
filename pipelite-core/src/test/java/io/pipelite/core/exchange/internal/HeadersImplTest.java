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

import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The value of a header is a scalar, kept with its type, and the mistake is refused where it is made (issue #166).
 */
public class HeadersImplTest {

    private final HeadersImpl subject = new HeadersImpl();

    private static final class Order implements Serializable {
    }

    @Test
    public void givenAScalar_thenTheHeaderKeepsItsExactType() {
        subject.putHeader("text", "abc");
        subject.putHeader("flag", true);
        subject.putHeader("byte", (byte) 1);
        subject.putHeader("short", (short) 2);
        subject.putHeader("int", 3);
        subject.putHeader("long", 4L);
        subject.putHeader("float", 5.5f);
        subject.putHeader("double", 6.5d);
        subject.putHeader("bigInteger", BigInteger.TEN);
        subject.putHeader("bigDecimal", new BigDecimal("7.25"));

        Assert.assertEquals(Integer.valueOf(3), subject.tryGetHeaderAs("int", Integer.class).orElseThrow());
        Assert.assertEquals(Long.valueOf(4L), subject.tryGetHeaderAs("long", Long.class).orElseThrow());
        Assert.assertEquals(Boolean.TRUE, subject.tryGetHeaderAs("flag", Boolean.class).orElseThrow());
        Assert.assertEquals(new BigDecimal("7.25"), subject.tryGetHeaderAs("bigDecimal", BigDecimal.class).orElseThrow());
        Assert.assertEquals(Short.valueOf((short) 2), subject.tryGetHeaderAs("short", Short.class).orElseThrow());
        Assert.assertEquals(10, subject.size());
    }

    @Test
    public void givenAnObjectOfTheApplication_thenItIsRefusedWithTheNameOfTheHeaderAndTheType() {
        final IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
            () -> subject.putHeader("order", new Order()));

        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("'order'"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(Order.class.getName()));
        Assert.assertFalse(subject.hasHeader("order"));
    }

    @Test
    public void givenACollectionAnArrayOrAClass_thenItIsRefused() {
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.putHeader("tags", new ArrayList<>(List.of("a"))));
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.putHeader("bytes", new byte[]{1}));
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.putHeader("type", String.class));
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.putHeader("when", java.time.Instant.now()));
    }

    @Test
    public void givenANumberThatIsMutable_thenItIsRefused() {
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.putHeader("counter", new AtomicInteger(1)));
    }

    @Test
    public void givenANullValue_thenTheHeaderIsRemoved() {
        subject.putHeader("x", "1");

        subject.putHeader("x", null);

        Assert.assertFalse(subject.hasHeader("x"));
        Assert.assertTrue(subject.tryGetHeader("x").isEmpty());
    }

    @Test
    public void givenTheHeadersAsAMap_thenTheSameRuleHolds() {
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.put("order", new Order()));
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.putAll(Map.of("order", new Order())));
        Assert.assertThrows(IllegalArgumentException.class, () -> new HeadersImpl(Map.<String, Object>of("order", new Order())));
        Assert.assertTrue(subject.isEmpty());

        subject.put("n", 1);
        Assert.assertEquals(1, subject.get("n"));
    }

    @Test
    public void givenANumberOrABoolean_thenTryGetHeaderGivesItsTextInsteadOfThrowing() {
        subject.putHeader("priority", 7);
        subject.putHeader("urgent", true);
        subject.putHeader("price", new BigDecimal("12.50"));

        Assert.assertEquals("7", subject.tryGetHeader("priority").orElseThrow());
        Assert.assertEquals("true", subject.tryGetHeader("urgent").orElseThrow());
        Assert.assertEquals("12.50", subject.tryGetHeader("price").orElseThrow());
        Assert.assertEquals("7", subject.expectHeader("priority"));
        Assert.assertTrue(subject.tryGetHeader("missing").isEmpty());
    }

    @Test
    public void givenTryGetHeaderAs_thenTheTypeIsStillChecked() {
        subject.putHeader("priority", 7);

        Assert.assertThrows(ClassCastException.class, () -> subject.tryGetHeaderAs("priority", String.class));
        Assert.assertEquals(7, subject.tryGetHeaderAs("priority", Number.class).orElseThrow().intValue());
    }

    @Test
    public void givenACopy_thenItHoldsTheSameScalarsAndIsIndependent() {
        subject.putHeader("n", 5L);

        final HeadersImpl copy = new HeadersImpl(subject);
        copy.putHeader("n", 6L);

        Assert.assertEquals(Long.valueOf(5L), subject.tryGetHeaderAs("n", Long.class).orElseThrow());
        Assert.assertEquals(Long.valueOf(6L), copy.tryGetHeaderAs("n", Long.class).orElseThrow());
    }
}
