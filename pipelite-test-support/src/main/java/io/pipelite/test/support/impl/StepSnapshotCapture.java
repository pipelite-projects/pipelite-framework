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
package io.pipelite.test.support.impl;

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.FlowExecutionContext;
import io.pipelite.test.PipeliteTestFixture;
import io.pipelite.test.ThenOperations;
import io.pipelite.test.support.matchers.Expectation;

import java.util.Map;

/**
 * Snapshots the exchange right after each step executes, keyed by step name,
 * so {@link ThenOperations#inspectStep(String, Expectation...)} can verify
 * intermediate state of an otherwise asynchronous flow.
 *
 * <p>Registered on every {@code FlowNode} of every registered flow definition
 * before the flow is deployed — see {@link PipeliteTestFixture#supplyTo(String)}.
 *
 * <p>Internal implementation detail of {@code pipelite-test-support}: public
 * only because it is constructed from the {@code io.pipelite.test} package,
 * not part of the supported public API (hence the {@code .support.impl} package).
 */
public class StepSnapshotCapture implements ExchangePostProcessor {

    private final Map<String, Exchange> snapshots;
    private final ExchangeFactory exchangeFactory;

    public StepSnapshotCapture(Map<String, Exchange> snapshots, ExchangeFactory exchangeFactory) {
        this.snapshots = snapshots;
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public void postProcess(FlowExecutionContext ctx, Exchange exchange) {
        snapshots.put(ctx.getProcessorName(), exchangeFactory.copyExchange(exchange));
    }
}
