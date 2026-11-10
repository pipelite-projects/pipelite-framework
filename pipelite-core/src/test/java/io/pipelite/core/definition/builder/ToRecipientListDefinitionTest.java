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
package io.pipelite.core.definition.builder;

import io.pipelite.core.Pipelite;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@code toRecipientList(...)} takes the fixed set of destinations and refuses a wrong one when the flow is defined
 * (issue #151).
 */
public class ToRecipientListDefinitionTest {

    @Test
    public void givenAFixedSetOfDestinations_thenTheFlowIsDefined() {
        Assert.assertNotNull(Pipelite.defineFlow("fan-out")
            .fromSource("queue://fan-out-in")
            .toRecipientList("queue://a-in", "queue://b-in", "slf4j://audit")
            .build());
    }

    @Test
    public void givenNoRecipient_thenItIsRefusedWithAClearMessage() {
        final IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
            () -> Pipelite.defineFlow("fan-out").fromSource("queue://fan-out-in").toRecipientList());

        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("at least one recipient"));
    }

    @Test
    public void givenARecipientThatIsNotAURL_thenItIsRefused() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> Pipelite.defineFlow("fan-out").fromSource("queue://fan-out-in").toRecipientList("queue://a-in", "b-in"));
    }
}
