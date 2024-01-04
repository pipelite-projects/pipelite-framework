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
package io.pipelite.core.flow.expression;

import io.pipelite.expression.ExpressionParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class TextExpressionEvaluatorTest {

    private TextExpressionEvaluator subject;

    @Before
    public void setup(){
        subject = new TextExpressionEvaluator(new ExpressionParser());
    }

    @Test
    public void shouldEvaluateSingleExpression(){

        final Map<String,Object> variables = new HashMap<>();
        variables.put("issuerId", "us-bank");
        final String result = subject.evaluateText("banking.#{issuerId}.topic", variables);

        Assert.assertEquals("banking.us-bank.topic", result);

    }

    @Test
    public void shouldEvaluateMultipleExpressions(){

        final Map<String,Object> variables = new HashMap<>();
        variables.put("issuerId", "us-bank");
        variables.put("topicId", "tx-approval");
        variables.put("partitionNumber", 1);
        final String result = subject.evaluateText("banking.#{issuerId}.topic.#{topicId}/#{partitionNumber}", variables);

        Assert.assertEquals("banking.us-bank.topic.tx-approval/1", result);

    }

    @Test
    public void shouldEvaluateNoExpression(){

        final Map<String,Object> variables = new HashMap<>();
        final String result = subject.evaluateText("banking.us-bank.topic.tx-approval/1", variables);

        Assert.assertEquals("banking.us-bank.topic.tx-approval/1", result);

    }
}
