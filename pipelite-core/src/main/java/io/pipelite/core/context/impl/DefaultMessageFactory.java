package io.pipelite.core.context.impl;

import io.pipelite.spi.flow.exchange.IdentityGenerator;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.MessageFactory;
import io.pipelite.spi.flow.exchange.SimpleMessage;

import java.util.Objects;

public class DefaultMessageFactory implements MessageFactory {

    private final IdentityGenerator identityGenerator;

    public DefaultMessageFactory(IdentityGenerator identityGenerator) {
        Objects.requireNonNull(identityGenerator, "identityGenerator is required and cannot be null");
        this.identityGenerator = identityGenerator;
    }

    @Override
    public Message createMessage() {
        final String nextId = identityGenerator.nextIdAsText();
        return new SimpleMessage(nextId);
    }

    @Override
    public Message copyMessage(Message message) {
        final Message copy = createMessage();
        copy.setCorrelationId(message.getId());
        copy.setPayload(message.getPayload());
        return copy;
    }
}
