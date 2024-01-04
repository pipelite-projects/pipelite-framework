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

import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.ScheduledPollingConsumerService;
import io.pipelite.spi.flow.concurrent.DefaultThreadFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.util.concurrent.Executors;

public class RetryService extends ScheduledPollingConsumerService implements ExchangeFactoryAware {

    private static final String THREAD_PREFIX = "retry-channel";

    public RetryService(Endpoint endpoint) {
        super(new RetryPollingConsumer(endpoint),
            Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory(THREAD_PREFIX)));
    }

    public void setFlowExecutionDumpRepository(FlowExecutionDumpRepository dumpRepository){
        ((RetryPollingConsumer)pollingConsumer).setDumpRepository(dumpRepository);
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        ((RetryPollingConsumer)pollingConsumer).setExchangeFactory(exchangeFactory);
    }
}
