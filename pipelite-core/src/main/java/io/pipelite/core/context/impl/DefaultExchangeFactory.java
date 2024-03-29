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
package io.pipelite.core.context.impl;

import io.pipelite.dsl.Headers;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.MessageFactory;

import java.util.Map;
import java.util.Objects;

public class DefaultExchangeFactory implements ExchangeFactory {

    private final MessageFactory messageFactory;

    public DefaultExchangeFactory(MessageFactory messageFactory) {
        Objects.requireNonNull(messageFactory, "messageFactory is required and cannot be null");
        this.messageFactory = messageFactory;
    }

    @Override
    public Exchange createExchange() {
        return createExchange(null, null);
    }

    @Override
    public Exchange createExchange(Headers headers) {
        return createExchange(headers, null);
    }

    @Override
    public Exchange createExchange(Object inputPayload) {
        return createExchange(null, inputPayload);
    }

    @Override
    public Exchange createExchange(Headers headers, Object inputPayload) {
        final Message inputMessage = messageFactory.createMessage();
        inputMessage.setPayload(inputPayload);
        final Exchange exchange = new Exchange(inputMessage, headers);
        exchange.setOutput(messageFactory.createMessage());
        return exchange;
    }

    @Override
    public Exchange copyExchange(Exchange current) {

        final Message inputMessage = current.getInput();
        final Message messageCopy = messageFactory.copyMessage(inputMessage);

        final Exchange copy = new Exchange(messageCopy, current.getHeaders());
        copyProperties(current, copy);
        copy.setOutput(current.getOutput());
        return copy;

    }

    @Override
    public Exchange nextExchange(Exchange current) {
        current.forwardIfNecessary();
        final Exchange exchange = new Exchange(current.getOutput(), current.getHeaders());
        exchange.setOutput(messageFactory.createMessage());
        copyProperties(current, exchange);
        return exchange;
    }

    private static void copyProperties(Exchange source, Exchange destination){
        source.propertySet().forEach(property -> {
            destination.setProperty(property.getKey(), property.getValue());
        });
    }

}
