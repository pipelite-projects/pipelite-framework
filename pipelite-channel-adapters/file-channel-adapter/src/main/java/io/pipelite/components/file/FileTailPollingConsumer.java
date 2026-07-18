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

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.endpoint.DefaultPollingConsumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointProperties;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileTailPollingConsumer extends DefaultPollingConsumer implements ExchangeFactoryAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Path path;
    private final Charset charset;
    private final String startPosition;
    private final FileRecordMapper<?> recordMapper;
    private final FileTailStateStore stateStore;

    private ExchangeFactory exchangeFactory;

    public FileTailPollingConsumer(Endpoint endpoint, FileChannelConfiguration configuration) {
        super(endpoint);

        Preconditions.notNull(configuration, "configuration is required and cannot be null");

        final EndpointURL endpointURL = endpoint.getEndpointURL();
        final EndpointProperties properties = endpointURL.getProperties();

        this.path = Path.of(endpointURL.getResource());
        this.charset = Charset.forName(properties.getOrDefault(FileConstants.CHARSET_PROPERTY_NAME, FileConstants.DEFAULT_CHARSET));
        this.startPosition = properties.getOrDefault(FileConstants.START_POSITION_PROPERTY_NAME, FileConstants.START_POSITION_END);
        this.recordMapper = configuration.resolveMapper(properties.get(FileConstants.RECORD_MAPPER_PROPERTY_NAME));
        this.stateStore = new FileTailStateStore(configuration.getStateDirectory());
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public Exchange receive() {
        return receive(0);
    }

    @Override
    public Exchange receive(long timeout) {
        Preconditions.notNull(exchangeFactory, "ExchangeFactory is required and cannot be null");
        pollAndEnqueueNewRecords();
        // Always drain the queue, even when this tick found no new content: a previous tick
        // may have enqueued more records than a single receive() call returns.
        return super.receive(timeout);
    }

    private void pollAndEnqueueNewRecords() {

        if (!Files.exists(path)) {
            return;
        }

        final long currentSize;
        try {
            currentSize = Files.size(path);
        } catch (IOException exception) {
            sysLogger.warn("Unable to read size of file '{}'", path, exception);
            return;
        }

        final String resourceKey = path.toString();
        long offset = resolveOffset(resourceKey, currentSize);

        if (currentSize < offset) {
            sysLogger.warn("File '{}' shrank from last known offset {} to size {}; resetting offset to 0", path, offset, currentSize);
            offset = 0L;
            stateStore.save(resourceKey, 0L);
        }

        if (currentSize == offset) {
            return;
        }

        final String newContent = readNewContent(offset, currentSize);
        final MappingResult<?> result = recordMapper.map(newContent);
        for (Object record : result.getRecords()) {
            final Exchange exchange = exchangeFactory.createExchange(record);
            exchange.putHeader(FileConstants.FILE_NAME_EXCHANGE_HEADER_NAME, path.getFileName().toString());
            exchange.putHeader(FileConstants.FILE_PATH_EXCHANGE_HEADER_NAME, path.toAbsolutePath().toString());
            consume(exchange);
        }

        stateStore.save(resourceKey, offset + result.getConsumedLength());
    }

    private long resolveOffset(String resourceKey, long currentSize) {
        final long persisted = stateStore.load(resourceKey);
        if (persisted == 0L && !FileConstants.START_POSITION_BEGINNING.equalsIgnoreCase(startPosition)) {
            stateStore.save(resourceKey, currentSize);
            return currentSize;
        }
        return persisted;
    }

    private String readNewContent(long offset, long currentSize) {
        try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
            fc.position(offset);
            final ByteBuffer buffer = ByteBuffer.allocate((int) (currentSize - offset));
            while (buffer.hasRemaining() && fc.read(buffer) != -1) {
                // keep reading until the buffer is full
            }
            buffer.flip();
            return charset.decode(buffer).toString();
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to read new content from file '%s'", path), exception);
        }
    }

}
