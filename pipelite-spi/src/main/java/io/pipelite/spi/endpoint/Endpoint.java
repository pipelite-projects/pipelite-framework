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
package io.pipelite.spi.endpoint;

import io.pipelite.dsl.Headers;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.flow.exchange.Exchange;

public interface Endpoint {

    EndpointURL getEndpointURL();

    EndpointProperties getProperties();

    Consumer createConsumer();
    //PollingConsumer createPollingConsumer(MessageSupplier supplier, ChainedProcessor processor);
    Producer createProducer();

    @Deprecated
    Exchange createExchange();
    @Deprecated
    Exchange createExchange(Headers headers);

    <T extends ChannelAdapter> T getChannelAdapter(Class<T> channelType);

    @Deprecated
    boolean isSink();

    @Deprecated
    void setSink();

}
