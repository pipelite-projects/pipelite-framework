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
package io.pipelite.core;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Issue #149: a split hands each element to the steps that follow, so "split, then send every element to a recipient
 * list" is written as a source, a split and a recipient list: no second flow and no use of the context by the user code.
 */
public class PipeliteSplitIntegrationTest {

    private DefaultPipeliteContext context;
    private final List<String> received = Collections.synchronizedList(new java.util.ArrayList<>());

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    @After
    public void tearDown() {
        context.stop();
    }

    private void jobFlow(String job) {
        context.registerFlowDefinition(Pipelite.defineFlow("job-" + job + "-flow")
            .fromSource("queue://job-" + job + "-requests")
            .process("run", (exchange, contribution) -> received.add(job + ":" + exchange.getInputPayload()))
            .build());
    }

    @Test
    public void givenAListOfItems_whenSplitThenARecipientList_thenEveryItemReachesEveryRecipient() {
        context.registerFlowDefinition(Pipelite.defineFlow("open-items-flow")
            .fromSource("queue://items-in")
            .split("open-items")
            .toRecipientList("queue://job-a-requests", "queue://job-b-requests", "queue://job-c-requests")
            .build());
        jobFlow("a");
        jobFlow("b");
        jobFlow("c");
        context.start();

        context.supplyExchange("queue://items-in", context.createExchange(List.of("site-1", "site-2")));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() == 6);
        for (String job : List.of("a", "b", "c")) {
            Assert.assertTrue(received.contains(job + ":site-1"));
            Assert.assertTrue(received.contains(job + ":site-2"));
        }
    }

    @Test
    public void givenAListOfItems_whenSplitThenARoute_thenEachItemIsRoutedOnItsOwn() {
        context.registerFlowDefinition(Pipelite.defineFlow("route-items-flow")
            .fromSource("queue://route-items-in")
            .split("open-items")
            .toRoute(route -> route.dynamic()
                .when("Payload == 'urgent'").then("queue://job-a-requests")
                .otherwise("queue://job-b-requests")
                .end())
            .build());
        jobFlow("a");
        jobFlow("b");
        context.start();

        context.supplyExchange("queue://route-items-in", context.createExchange(List.of("urgent", "normal", "urgent")));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() == 3);
        Assert.assertEquals(2, received.stream().filter(entry -> entry.equals("a:urgent")).count());
        Assert.assertEquals(1, received.stream().filter(entry -> entry.equals("b:normal")).count());
    }
}
