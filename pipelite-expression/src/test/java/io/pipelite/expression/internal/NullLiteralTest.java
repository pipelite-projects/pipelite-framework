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
package io.pipelite.expression.internal;

import io.pipelite.expression.ExpressionParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code null} literal can be compared: {@code Payload != null} is the first example of the Expressions page. It used
 * to throw a {@code NullPointerException} when the evaluation looked the literal up as a variable.
 * <p>
 * An evaluation clears the variables, so each one sets them again.
 */
public class NullLiteralTest {

    private final ExpressionParser parser = new ExpressionParser();

    @Test
    public void givenAValue_whenComparedWithNullForInequality_thenTrue() {
        parser.putVariable("Payload", "abc");

        Assert.assertTrue(parser.evaluateAs("Payload != null", Boolean.class));
    }

    @Test
    public void givenAValue_whenComparedWithNullForEquality_thenFalse() {
        parser.putVariable("Payload", "abc");

        Assert.assertFalse(parser.evaluateAs("Payload == null", Boolean.class));
    }

    @Test
    public void givenANullVariable_whenComparedForEquality_thenTrue() {
        parser.putVariable("Payload", null);

        Assert.assertTrue(parser.evaluateAs("Payload == null", Boolean.class));
    }

    @Test
    public void givenANullVariable_whenComparedForInequality_thenFalse() {
        parser.putVariable("Payload", null);

        Assert.assertFalse(parser.evaluateAs("Payload != null", Boolean.class));
    }

    @Test
    public void givenAMissingHeader_whenComparedWithNull_thenEqual() {
        parser.putVariable("Headers", headers());

        Assert.assertTrue(parser.evaluateAs("Headers['other'] == null", Boolean.class));
    }

    @Test
    public void givenAPresentHeader_whenComparedWithNull_thenNotEqual() {
        parser.putVariable("Headers", headers());

        Assert.assertFalse(parser.evaluateAs("Headers['type'] == null", Boolean.class));
    }

    @Test
    public void givenTheLiteralInUpperCase_thenItIsNull() {
        parser.putVariable("Payload", "abc");

        Assert.assertTrue(parser.evaluateAs("Payload != NULL", Boolean.class));
    }

    @Test
    public void givenTheLiteralAlone_thenTheResultIsNull() {
        Assert.assertNull(parser.evaluateAs("null", Object.class));
    }

    @Test
    public void givenANullVariable_whenAPropertyOfItIsRead_thenItIsNull() {
        parser.putVariable("Payload", null);

        Assert.assertTrue(parser.evaluateAs("Payload.name == null", Boolean.class));
    }

    private static Map<String, Object> headers() {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("type", "A");
        return headers;
    }
}
