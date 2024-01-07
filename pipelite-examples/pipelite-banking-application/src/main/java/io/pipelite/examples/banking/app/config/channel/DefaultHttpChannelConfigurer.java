package io.pipelite.examples.banking.app.config.channel;

import io.pipelite.components.http.config.HttpChannelConfiguration;
import io.pipelite.components.http.config.HttpChannelConfigurer;
import io.pipelite.components.http.config.RequestMapping;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;
import io.pipelite.examples.banking.infrastructure.support.serialization.DefaultJacksonMapperConfigurator;

public class DefaultHttpChannelConfigurer implements HttpChannelConfigurer {

    @Override
    public void configure(HttpChannelConfiguration configuration) {

        configuration.setObjectMapperConfigurator(new DefaultJacksonMapperConfigurator());

        configuration.addRequestMapping(RequestMapping.define("/banking-payment-channel")
            .withPostMethod()
            .withContentType("application/json")
            .withPayloadType(TransactionMessage.class)
            .build());

    }
}
