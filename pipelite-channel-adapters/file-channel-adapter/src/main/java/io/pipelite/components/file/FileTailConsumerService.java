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
package io.pipelite.components.file;

import io.pipelite.spi.endpoint.ScheduledPollingConsumerService;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;

public class FileTailConsumerService extends ScheduledPollingConsumerService implements ExchangeFactoryAware {

    private final Path stateDirectory;

    public FileTailConsumerService(FileTailPollingConsumer pollingConsumer, ScheduledExecutorService consumerPool, Path stateDirectory) {
        super(pollingConsumer, consumerPool);
        this.stateDirectory = stateDirectory;
    }

    @Override
    public void doStart() {
        try {
            Files.createDirectories(stateDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to create state directory '%s'", stateDirectory), exception);
        }
        super.doStart();
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        if (pollingConsumer instanceof ExchangeFactoryAware) {
            ((ExchangeFactoryAware) pollingConsumer).setExchangeFactory(exchangeFactory);
        }
    }

}
