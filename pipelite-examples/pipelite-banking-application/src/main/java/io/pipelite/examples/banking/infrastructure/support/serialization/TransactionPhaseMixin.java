package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "transactionPhase")
/*
@JsonSubTypes({
    @JsonSubTypes.Type(value = TransactionRequest.class, name = "TRANSACTION_REQUEST"),
    @JsonSubTypes.Type(value = TransactionRequest.class, name = "TRANSACTION_RESPONSE")
})*/
public abstract class TransactionPhaseMixin {
}
