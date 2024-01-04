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
package io.pipelite.core.flow.execution.retry;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.spi.endpoint.DefaultPollingConsumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.util.Optional;

public class RetryPollingConsumer extends DefaultPollingConsumer implements ExchangeFactoryAware {

    private FlowExecutionDumpRepository dumpRepository;

    private ExchangeFactory exchangeFactory;

    public RetryPollingConsumer(Endpoint endpoint) {
        super(endpoint, 1);
    }

    public void setDumpRepository(FlowExecutionDumpRepository dumpRepository) {
        this.dumpRepository = dumpRepository;
    }

    @Override
    public Exchange receive() {
        return receive(0);
    }

    @Override
    public Exchange receive(long timeout) {

        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");
        final Optional<FlowExecutionDump> nextDumpHolder = dumpRepository.poll();
        if(nextDumpHolder.isPresent()) {
            final FlowExecutionDump nextDump = nextDumpHolder.get();
            return exchangeFactory.createExchange(nextDump);
        }
        return null;
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }
}
