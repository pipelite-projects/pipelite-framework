package io.pipelite.core.flow.process;

import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

public abstract class AbstractProcessorNode extends AbstractFlowNode implements FlowNode, ExchangeFactoryAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Processor delegate;

    private ExchangeFactory exchangeFactory;

    public AbstractProcessorNode(Processor delegate) {
        Objects.requireNonNull(delegate, "delegate is required and cannot be null");
        this.delegate = delegate;
    }

    @Override
    public void process(Exchange exchange) {

        final ProcessContribution contribution = new ProcessContributionImpl();

        try {
            preProcessExchange(exchange);
            delegate.process(exchange, contribution);
            postProcessExchange(exchange);
            if(tag != null && sysLogger.isTraceEnabled()){
                sysLogger.trace("{} - Exchange successfully processed", tag);
            }
        }catch(RuntimeException exception) {
            if(sysLogger.isErrorEnabled()){
                sysLogger.error("An underlying error occurred processing message", exception);
            }
            if(exceptionHandler != null){
                exceptionHandler.handleException(exception, exchange);
                return;
            }else{
                throw exception;
            }
        }

        if(next != null && !contribution.isExecutionStopped()){
            final Exchange nextExchange = exchangeFactory.nextExchange(exchange);
            next.process(nextExchange);
        }
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

}
