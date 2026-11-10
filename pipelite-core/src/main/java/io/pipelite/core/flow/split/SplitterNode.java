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
package io.pipelite.core.flow.split;

import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.dsl.split.SplitHeaders;

import java.util.Collection;
import java.util.Objects;

/**
 * Splits a {@code Collection} payload into one exchange per element, and sends each of them on through the nodes that
 * follow (issue #149): the elements flow on as exchanges of their own, to a recipient list, a route, a sink or any
 * step, and nothing puts them back together unless an {@code aggregate} step does.
 * <p>
 * The elements go one after the other on the thread of the flow, and the node returns when the last of them has gone
 * through the rest of the flow, so the execution of the exchange that was split ends then: the barriers that await
 * the flow, the key of an ordered source and the entry of the durable inbox are let go after the last element. If
 * the process stops before, the exchange that was split is delivered again and splits again; the key of each
 * element, the one of the exchange followed by its position, is the same each time.
 * <p>
 * An element is an exchange of the same execution ({@link ExchangeFactory#deriveExchange}): it has the headers and
 * the properties of the exchange that was split, except the inbox entry and the state of its attempts. A step that
 * fails for one element is handled for that element: its failure is retried or dead-lettered on its own, and the
 * other elements go on.
 * <p>
 * Package-private since #82: construct via {@link SplitNodeFactory#splitter()}.
 */
class SplitterNode extends AbstractFlowNode implements PipeliteContextAware {

    private ExchangeFactory exchangeFactory;

    @Override
    public void setPipeliteContext(InternalPipeliteContext pipeliteContext) {
        Objects.requireNonNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
    }

    @Override
    public void process(ExchangeImpl exchange) {

        final Collection<?> elements;
        try {
            preProcessExchange(exchange);
            elements = elementsOf(exchange);
        } catch (RuntimeException exception) {
            // Nothing was split yet: the failure is the one of the exchange that reached the step.
            handleFailure(exception, exchange);
            return;
        }

        if (next == null) {
            return;
        }

        final String splitId = exchange.getInput().getId();
        final int size = elements.size();
        final String idempotencyKey = exchange.getIdempotencyKey();
        int position = 0;
        for (Object element : elements) {
            final ExchangeImpl child = exchangeFactory.deriveExchange(exchange, element);
            child.putHeader(SplitHeaders.ID, splitId);
            child.putHeader(SplitHeaders.INDEX, position);
            child.putHeader(SplitHeaders.SIZE, size);
            if (idempotencyKey != null) {
                // A piece of work of its own: the same one every time the split is run again.
                child.setProperty(IOKeys.IDEMPOTENCY_KEY_PROPERTY_NAME, idempotencyKey + "-" + position);
            }
            position++;
            postProcessExchange(child);
            next.process(exchangeFactory.nextExchange(child));
        }
    }

    private static Collection<?> elementsOf(ExchangeImpl exchange) {
        final Object payload = exchange.getInputPayloadAs(Object.class);
        if (payload instanceof Collection<?> collection) {
            return collection;
        }
        throw new IllegalArgumentException(String.format(
            "A split needs a Collection as the payload, and the payload is %s",
            payload == null ? "null" : "a " + payload.getClass().getName()));
    }
}
