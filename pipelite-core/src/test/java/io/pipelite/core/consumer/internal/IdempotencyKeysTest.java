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
package io.pipelite.core.consumer.internal;

import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.execution.inbox.DurableInbox;
import io.pipelite.core.flow.execution.inbox.InboxEntry;
import io.pipelite.core.flow.execution.inbox.SegmentedLogDurableInboxProvider;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

/** Issue #40: the key is fixed where the exchange enters a flow, before the inbox keeps it, and never replaced. */
public class IdempotencyKeysTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final DistributedIdentityGeneratorImpl identity = new DistributedIdentityGeneratorImpl();
    private final ExchangeFactory exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(identity));

    private EventDrivenConsumer consumerWithInbox() {
        final Endpoint endpoint = (Endpoint) () -> EndpointURL.parse("customer-updates");
        final EventDrivenConsumer consumer = new EventDrivenConsumer(endpoint);
        consumer.setFlowName("customer-updates");
        consumer.setProcessorName("customer-updates-source");
        final DurableInbox inbox = new SegmentedLogDurableInboxProvider(temporaryFolder.getRoot().toPath(), identity)
            .forFlow("customer-updates");
        consumer.setDurableInbox(inbox);
        return consumer;
    }

    private List<InboxEntry> pending(EventDrivenConsumer consumer) {
        return new SegmentedLogDurableInboxProvider(temporaryFolder.getRoot().toPath(), identity)
            .forFlow("customer-updates").pendingEntries();
    }

    @Test
    public void givenAnExchangeWithoutKey_thenItGetsTheIdOfItsInputMessage() {
        final ExchangeImpl exchange = exchangeFactory.createExchange("payload");

        IdempotencyKeys.ensure(exchange);

        Assert.assertEquals(exchange.getInput().getId(), exchange.getIdempotencyKey());
    }

    @Test
    public void givenAnExchangeThatHasAKey_thenItIsKept() {
        final ExchangeImpl exchange = exchangeFactory.createExchange("payload");
        exchange.setProperty(IOKeys.IDEMPOTENCY_KEY_PROPERTY_NAME, "key-from-upstream");

        IdempotencyKeys.ensure(exchange);

        Assert.assertEquals("key-from-upstream", exchange.getIdempotencyKey());
    }

    @Test
    public void givenAnExchangeThatEntersAFlow_thenTheEntryOfTheInboxCarriesTheKeyAndAReplayKeepsIt() {
        final EventDrivenConsumer consumer = consumerWithInbox();
        final ExchangeImpl exchange = exchangeFactory.createExchange("payload");

        consumer.process(exchange);

        final String key = exchange.getIdempotencyKey();
        Assert.assertNotNull(key);
        final List<InboxEntry> entries = pending(consumer);
        Assert.assertEquals(1, entries.size());
        final ExchangeImpl given = new ByteArrayToObjectConverter().convert(entries.get(0).getPayload(), ExchangeImpl.class);
        Assert.assertEquals("what the inbox gives back after a restart has the key it had", key, given.getIdempotencyKey());

        IdempotencyKeys.ensure(given);
        Assert.assertEquals(key, given.getIdempotencyKey());
    }
}
