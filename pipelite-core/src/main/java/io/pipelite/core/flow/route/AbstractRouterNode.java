package io.pipelite.core.flow.route;

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.flow.exchange.FlowNode;

@Deprecated
public abstract class AbstractRouterNode implements FlowNode, ExchangeFactoryAware {

    protected ExchangeFactory exchangeFactory;

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public abstract void process(Exchange exchange);

    @Override
    public void setNext(FlowNode next) {
        /* do nothing */
    }

    @Override
    public boolean hasNext() {
        return false;
    }

}
