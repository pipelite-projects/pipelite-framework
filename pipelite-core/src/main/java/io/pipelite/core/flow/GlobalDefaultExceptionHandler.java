package io.pipelite.core.flow;

import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.exchange.Exchange;

public class GlobalDefaultExceptionHandler implements ExceptionHandler {

    public GlobalDefaultExceptionHandler() {
    }

    @Override
    public void handleException(Throwable exception, Exchange exchange) {

    }

}
