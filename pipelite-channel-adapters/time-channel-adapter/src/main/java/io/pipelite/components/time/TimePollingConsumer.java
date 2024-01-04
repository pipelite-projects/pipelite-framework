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
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.time.LocalDateTime;

public class TimePollingConsumer extends DefaultPollingConsumer implements ExchangeFactoryAware {

    private ExchangeFactory exchangeFactory;

    public TimePollingConsumer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public Exchange receive() {
        return receive(0);
    }

    @Override
    public Exchange receive(long timeout) {
        Preconditions.notNull(exchangeFactory, "ExchangeFactory is required and cannot be null");
        return exchangeFactory.createExchange(LocalDateTime.now());
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

}
