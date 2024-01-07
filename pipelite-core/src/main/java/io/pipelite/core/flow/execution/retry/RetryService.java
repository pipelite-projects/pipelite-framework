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
