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

import io.pipelite.spi.flow.exchange.Exchange;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class DefaultPollingConsumer extends AbstractConsumer implements PollingConsumer {

    private final Object LOCK = new Object();

    protected final BlockingQueue<Exchange> queue;

    public DefaultPollingConsumer(Endpoint endpoint) {
        this(endpoint, Integer.MAX_VALUE);
    }

    public DefaultPollingConsumer(Endpoint endpoint, int queueSize) {
        super(endpoint);
        queue = new LinkedBlockingDeque<>(queueSize);
    }

    @Override
    public Exchange receive() {
        synchronized (LOCK){
            return queue.poll();
        }
    }

    @Override
    public Exchange receive(long timeout) {
        synchronized (LOCK) {
            try {
                return queue.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    @Override
    public Exchange receiveNoWait() {
        return receive(0);
    }

    @Override
    public void consume(Exchange exchange) {
        synchronized (LOCK){
            try{
                queue.put(exchange);
            }catch(InterruptedException exception){
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void process(Exchange exchange) {
        if(next != null){
            next.process(exchange);
        }
    }
}
