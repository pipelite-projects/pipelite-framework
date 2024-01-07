package io.pipelite.core.flow.process.transform;

import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.PayloadHolder;
import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;

import java.util.Arrays;
import java.util.Collection;

public class PayloadTransformerNode implements Processor {

    private final Collection<Class<?>> WRAPPER_TYPES = Arrays.asList(new Class<?>[]{
        String.class, Integer.class, Byte.class, Character.class, Boolean.class, Double.class, Float.class, Long.class, Short.class
    });

    private final PayloadTransformer payloadTransformer;

    public PayloadTransformerNode(PayloadTransformer payloadTransformer) {
        this.payloadTransformer = payloadTransformer;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {
        final Object outputPayload = payloadTransformer.transform(new PayloadHolder(ioContext.getInputPayload()));
        ioContext.setOutputPayload(outputPayload);
    }

    @SuppressWarnings("unused")
    private boolean isWrapperType(Class<?> payloadType){
        return WRAPPER_TYPES
            .stream()
            .anyMatch(wrapperType ->
                wrapperType.isAssignableFrom(payloadType));
    }
}
