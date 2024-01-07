package io.pipelite.dsl.process;

public interface PayloadTransformer {

    Object transform(PayloadHolder inputPayload);

}
