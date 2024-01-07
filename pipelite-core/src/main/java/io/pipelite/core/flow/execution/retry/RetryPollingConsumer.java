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
