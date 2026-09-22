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
package io.pipelite.components.time;

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.endpoint.DefaultPollingConsumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.time.LocalDateTime;

public class TimePollingConsumer extends DefaultPollingConsumer implements ExchangeFactoryAware {

    private ExchangeFactory exchangeFactory;

    public TimePollingConsumer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public ExchangeImpl receive() {
        return receive(0);
    }

    /**
     * A queued exchange first (issue #115) - the only way one gets onto {@code queue} is a plain
     * {@code consume(...)}, which the recovery of a durable-inbox entry and the fallback of a
     * retry that cannot find its failed processor ({@code SupplyExchangeProcessor}, since #108)
     * both use - and only when there is none, a fresh tick. Before this, an entry recovered onto a
     * {@code time://} source sat on {@code queue} forever: this method never looked at it, so it
     * was never executed and never acknowledged, and kept being recovered again on every restart.
     * {@link DefaultPollingConsumer#receive(long)} never blocks here, since {@link
     * io.pipelite.spi.endpoint.ScheduledPollingConsumerService} only ever calls {@link #receive()}
     * (timeout 0); a genuine positive timeout would make this wait for a queued exchange up to
     * that long before falling back to a tick, same as {@code FileTailPollingConsumer}.
     */
    @Override
    public ExchangeImpl receive(long timeout) {
        Preconditions.notNull(exchangeFactory, "ExchangeFactory is required and cannot be null");
        final ExchangeImpl queued = super.receive(timeout);
        if (queued != null) {
            return queued;
        }
        return exchangeFactory.createExchange(LocalDateTime.now());
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

}
