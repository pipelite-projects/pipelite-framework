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
package io.pipelite.core.source.internal;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.Exchange;
import io.pipelite.core.consumer.internal.DefaultPollingConsumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeFactoryAware;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.spi.source.PollingSource;

/**
 * What the polling service asks for the next exchange: first what is already queued (an entry recovered
 * from the durable inbox is delivered before anything new is polled), then the adapter's {@link
 * PollingSource}. A source that reads several records at once hands them over with {@code accept}, which
 * queues them, and returns {@code null} from {@code poll}: what it handed over is taken from the queue
 * right after, in the same call.
 */
final class PollingSourceConsumer extends DefaultPollingConsumer implements ExchangeFactoryAware {

    private final PollingSource source;
    private ExchangeFactory exchangeFactory;

    PollingSourceConsumer(Endpoint endpoint, PollingSource source) {
        super(endpoint);
        this.source = source;
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    Exchange newExchange(Object payload) {
        Preconditions.notNull(exchangeFactory, "ExchangeFactory is required and cannot be null");
        return exchangeFactory.createExchange(payload);
    }

    @Override
    public ExchangeImpl receive() {
        return receive(0);
    }

    @Override
    public ExchangeImpl receive(long timeout) {
        final ExchangeImpl queued = super.receive(timeout);
        if (queued != null) {
            return queued;
        }
        final Exchange polled = source.poll();
        if (polled != null) {
            return SourceExchanges.asExchangeImpl(polled);
        }
        return super.receive(0);
    }
}
