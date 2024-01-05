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
package io.pipelite.expression.core;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.awaitility.Awaitility;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.pipelite.expression.Expression;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.expression.core.context.EvaluationContext;

public class ConcurrentEvaluationTest extends ExpressionTestSupport {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentEvaluationTest.class);

    @Test
    public void shouldEvaluateConcurrently() {

        final ExpressionParser parser = new ExpressionParser();
        final ExecutorService executor = Executors.newFixedThreadPool(10);
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            executor.submit(
                    new EvaluationTask(parser, String.format("%s >= %s", random.nextInt(20), random.nextInt(20))));
        }
        executor.shutdown();
        Awaitility.await().until(executor::isTerminated);
    }

    @Test()
    public void shouldThrowExceptionOnConcurrentEvaluation() throws InterruptedException, ExecutionException {

        final ExecutorService executor = Executors.newFixedThreadPool(3);
        final Expression expression = newExpression(
                "(x >= y) && (1+4 >= 2) && y+2 < x && ((x >= y) && (1+4 >= 2) || y+2 < x) && (x >= y) || (1+4 >= 2) && y+2 < x && ((x >= y) && (1+4 >= 2) || y+2 < x)");
        final EvaluationContext evaluationContext = newEvaluationContext();
        final Random random = new Random();

        for (int i = 0; i < 3; i++) {
            Future<?> future = executor.submit(() -> {
                evaluationContext.putVariable("x", random.nextInt(10));
                evaluationContext.putVariable("y", random.nextInt(10));
                Boolean result = expression.evaluateAs(Boolean.class, evaluationContext);
                logger.info(String.format("%s evaluates %s with %s, result %s", Thread.currentThread(),
                        expression.asText(), evaluationContext.getVariableContext(), result));
            });
            future.get();
        }
        executor.shutdown();
        Awaitility.await().dontCatchUncaughtExceptions().until(executor::isTerminated);
    }

    public final static class EvaluationTask implements Runnable {

        private final ExpressionParser parser;

        private final String expression;

        public EvaluationTask(ExpressionParser parser, String expression) {
            this.parser = parser;
            this.expression = expression;
        }

        @Override
        public void run() {
            Boolean result = parser.evaluateAs(expression, Boolean.class);
            logger.debug(String.format("%s evaluates %s with result %s", Thread.currentThread().toString(), expression,
                    result));
        }

        public EvaluationTask putVariable(String name, Object value) {
            parser.putVariable(name, value);
            return this;
        }

    }

}
