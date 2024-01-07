package io.pipelite.core.context;

import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;

public interface PipeliteContext {

    void registerFlowDefinition(FlowDefinition flowDefinition);

    void addChannelConfigurer(ChannelConfigurer<?> configurer);

    boolean isRegistered(String flowName);

    void supplyExchange(String endpointURL, Exchange exchange);

    EndpointFactory getEndpointFactory();

    ExchangeFactory getExchangeFactory();

    void start();

    void stop();

}
