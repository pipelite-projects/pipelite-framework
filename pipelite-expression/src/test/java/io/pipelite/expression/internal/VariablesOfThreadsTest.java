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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The variables of an evaluation belong to the thread that set them. They used to be held by an inheritable
 * thread local, so a thread created by one that had already evaluated an expression shared its context: the
 * workers of a flow, setting {@code Payload} and {@code Headers} on a shared parser before evaluating, read each
 * other's values.
 */
public class VariablesOfThreadsTest {

    @Test
    public void givenThreadsCreatedByAThreadThatAlreadyEvaluated_thenEachEvaluationSeesItsOwnVariables() throws Exception {

        // One parser shared by the threads, and the variables set before the evaluation, as the filter and the
        // conditions of a route do; the creating thread has evaluated once, so it has a context of its own.
        final ExpressionParser parser = new ExpressionParser();
        parser.putVariable("Payload", "the creating thread");
        Assert.assertEquals("the creating thread", parser.evaluateAsText("Payload"));

        final int threads = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<String> wrong = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < threads; i++) {
                final String expected = "value-of-thread-" + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int n = 0; n < 300; n++) {
                            parser.putVariable("Payload", expected);
                            final String actual = parser.evaluateAsText("Payload");
                            if (!expected.equals(actual)) {
                                wrong.add(expected + " read " + actual);
                            }
                        }
                    } catch (Throwable throwable) {
                        wrong.add(expected + " failed: " + throwable);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            Assert.assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        }

        Assert.assertTrue(wrong.size() + " evaluations read the variables of another thread, for instance " + wrong.stream().limit(3).toList(),
            wrong.isEmpty());
    }
}
