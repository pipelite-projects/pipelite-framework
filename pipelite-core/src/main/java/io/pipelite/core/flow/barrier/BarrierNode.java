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
package io.pipelite.core.flow.barrier;

import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.definition.builder.barrier.BarrierSpec;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.HeadersImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.dsl.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Holds the exchanges of parallel flows that arrive here until they are complete, then lets one exchange go on
 * (issue #150). Each arrival is written to the {@link BarrierRepository} before this node returns, and returning
 * is what acknowledges the arrival to the durable inbox of its flow: an exchange that has to wait is therefore
 * safe. The arrival that completes the barrier carries on with the rest of the flow on its own thread, and the
 * state is marked done only when that has returned, so a crash in between replays the release (at-least-once
 * downstream, like everything else in the framework).
 * <p>
 * Package-private (#82): construct via {@link BarrierNodeFactory#barrier(BarrierSpec)}.
 */
class BarrierNode extends AbstractFlowNode implements PipeliteContextAware {

    private enum Kind {
        WAITING, IGNORED, COMPLETED, FAILED
    }

    private record Arrival(Kind kind, String key, String contributor, List<BarrierContribution> contributions) {
    }

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final BarrierSpec spec;
    private final Supplier<Instant> clock;
    private ExchangeFactory exchangeFactory;
    private BarrierRepository repository;

    BarrierNode(BarrierSpec spec) {
        this(spec, Instant::now);
    }

    BarrierNode(BarrierSpec spec, Supplier<Instant> clock) {
        this.spec = Objects.requireNonNull(spec, "spec is required and cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock is required and cannot be null");
    }

    @Override
    public void setPipeliteContext(InternalPipeliteContext pipeliteContext) {
        Objects.requireNonNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
        this.repository = pipeliteContext.getBarrierRepository();
    }

    /** Stable and addressable, so the state of this barrier can be found by name. */
    String barrierId() {
        return getFlowName() + "/" + getProcessorName();
    }

    @Override
    public void process(ExchangeImpl exchange) {

        final Arrival arrival;
        try {
            preProcessExchange(exchange);
            arrival = register(exchange);
        } catch (RuntimeException exception) {
            sysLogger.error("An underlying error occurred processing message", exception);
            handleFailure(exception, exchange);
            return;
        }

        switch (arrival.kind()) {
            case WAITING -> {
                sysLogger.debug("Barrier '{}' key '{}': '{}' arrived, waiting for the others", barrierId(), arrival.key(), arrival.contributor());
                postProcessExchange(exchange);
            }
            case IGNORED -> {
                sysLogger.debug("Barrier '{}' key '{}': '{}' ignored, the barrier is already closed or it had already arrived",
                    barrierId(), arrival.key(), arrival.contributor());
                postProcessExchange(exchange);
            }
            case FAILED -> {
                final List<String> contributors = arrival.contributions().stream().map(BarrierContribution::getContributorId).toList();
                handleFailure(new BarrierFailedException(barrierId(), arrival.key(), arrival.contributor(), contributors), exchange);
                markDone(arrival.key());
            }
            case COMPLETED -> release(exchange, arrival);
        }
    }

    private Arrival register(ExchangeImpl exchange) {

        final String barrierId = barrierId();
        final String key = spec.keyOf(exchange);
        final String contributor = spec.contributorOf(exchange);
        if (!spec.expects(contributor)) {
            throw new IllegalStateException(String.format("Barrier '%s' does not wait for the contributor '%s' (expected: %s)",
                barrierId, contributor, spec.getExpectedContributors()));
        }
        final boolean failure = spec.isFailure(exchange);
        final BarrierContribution contribution = BarrierContribution.of(contributor, exchange);

        return repository.update(barrierId, key,
            () -> BarrierState.open(barrierId, key, clock.get(), spec.getTimeout()),
            state -> arrive(state, contribution, failure, key));
    }

    /** Runs alone for the key: the one place where a contribution is counted and the barrier is closed. */
    private Arrival arrive(BarrierState state, BarrierContribution contribution, boolean failure, String key) {

        final String contributor = contribution.getContributorId();
        switch (state.getStatus()) {
            case DONE:
                return new Arrival(Kind.IGNORED, key, contributor, List.of());
            case COMPLETED:
            case FAILED:
                // The contributor that closed the state, replayed after a crash, closes it again: the rest of
                // the flow did not finish. Any other arrival is late.
                if (contributor.equals(state.getClosedBy())) {
                    return new Arrival(state.getStatus() == BarrierState.Status.FAILED ? Kind.FAILED : Kind.COMPLETED,
                        key, contributor, state.contributions());
                }
                return new Arrival(Kind.IGNORED, key, contributor, List.of());
            default:
                if (state.hasContributor(contributor)) {
                    return new Arrival(Kind.IGNORED, key, contributor, List.of());
                }
                state.add(contribution);
                if (failure) {
                    state.close(BarrierState.Status.FAILED, contributor);
                    return new Arrival(Kind.FAILED, key, contributor, state.contributions());
                }
                if (spec.isCompletedBy(state.contributors())) {
                    state.close(BarrierState.Status.COMPLETED, contributor);
                    return new Arrival(Kind.COMPLETED, key, contributor, state.contributions());
                }
                return new Arrival(Kind.WAITING, key, contributor, List.of());
        }
    }

    private void release(ExchangeImpl current, Arrival arrival) {

        final ExchangeImpl released;
        try {
            released = buildReleased(current, arrival.contributions());
            postProcessExchange(released);
        } catch (RuntimeException exception) {
            sysLogger.error("An underlying error occurred releasing the barrier", exception);
            handleFailure(exception, current);
            markDone(arrival.key());
            return;
        }

        if (next != null) {
            next.process(exchangeFactory.nextExchange(released));
        }
        markDone(arrival.key());
    }

    /**
     * What goes on: the headers of the first contribution to arrive (the same whichever one completes the barrier,
     * and after a restart), the properties of the exchange this thread is processing (the inbox entry, the retry
     * dump), and the payload the release policy makes of the contributions.
     */
    private ExchangeImpl buildReleased(ExchangeImpl current, List<BarrierContribution> contributions) {

        final BarrierContribution first = contributions.get(0);
        final Object payload = switch (spec.getRelease().getKind()) {
            case FIRST -> first.getPayload();
            case ALL -> {
                final List<Object> payloads = new ArrayList<>();
                contributions.forEach(contribution -> payloads.add(contribution.getPayload()));
                yield payloads;
            }
            case AGGREGATE -> spec.getRelease().getAggregator().apply(asExchanges(contributions));
        };

        final ExchangeImpl released = exchangeFactory.createExchange(first.getHeaders(), first.getPayload());
        current.propertySet().forEach(property -> released.setProperty(property.getKey(), property.getValue()));
        released.setOutputPayload(payload);
        return released;
    }

    private List<Exchange> asExchanges(List<BarrierContribution> contributions) {
        final List<Exchange> exchanges = new ArrayList<>();
        for (BarrierContribution contribution : contributions) {
            exchanges.add(exchangeFactory.createExchange(new HeadersImpl(contribution.getHeaders()), contribution.getPayload()));
        }
        return exchanges;
    }

    private void markDone(String key) {
        repository.update(barrierId(), key, () -> null, state -> {
            state.markDone();
            return null;
        });
    }
}
