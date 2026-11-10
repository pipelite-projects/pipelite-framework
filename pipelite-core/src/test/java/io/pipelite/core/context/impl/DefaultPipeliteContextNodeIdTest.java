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
package io.pipelite.core.context.impl;

import org.junit.Assert;
import org.junit.Test;

/**
 * The node id of a context (issue #169) reaches the identifiers it generates, and the message ids are the ones the
 * exchange factory of the context gives.
 */
public class DefaultPipeliteContextNodeIdTest {

    /** The text of an identifier is the hexadecimal of the number: the node id is its 10 bits above the sequence's 12. */
    private static int nodeIdOfMessage(DefaultPipeliteContext context) {
        final long id = Long.parseLong(context.getExchangeFactory().createExchange("payload").getInput().getId(), 16);
        return (int) ((id >> 12) & 0x3FF);
    }

    @Test
    public void givenANodeId_thenTheMessageIdsCarryIt() {
        final DefaultPipeliteContext context = new DefaultPipeliteContext();

        context.setNodeId(731);

        Assert.assertEquals(731, nodeIdOfMessage(context));
    }

    @Test
    public void givenTwoContextsWithDifferentNodeIds_thenTheirMessageIdsAreNeverTheSame() {
        final DefaultPipeliteContext first = new DefaultPipeliteContext();
        final DefaultPipeliteContext second = new DefaultPipeliteContext();
        first.setNodeId(10);
        second.setNodeId(11);

        for (int i = 0; i < 1_000; i++) {
            Assert.assertNotEquals(first.getExchangeFactory().createExchange("a").getInput().getId(),
                second.getExchangeFactory().createExchange("a").getInput().getId());
        }
    }

    @Test
    public void givenANodeIdOutOfRange_thenTheContextRefusesIt() {
        final DefaultPipeliteContext context = new DefaultPipeliteContext();

        Assert.assertThrows(IllegalArgumentException.class, () -> context.setNodeId(1024));
        Assert.assertThrows(IllegalArgumentException.class, () -> context.setNodeId(-1));
    }
}
