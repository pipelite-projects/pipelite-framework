package io.pipelite.core.flow.execution.retry;

import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.EndpointURL;

public class RetryEndpoint extends DefaultEndpoint {

    public RetryEndpoint(EndpointURL endpointURL) {
        super(endpointURL);
    }

    @Override
    public Consumer createConsumer() {
        return new RetryService(this);
    }
}
