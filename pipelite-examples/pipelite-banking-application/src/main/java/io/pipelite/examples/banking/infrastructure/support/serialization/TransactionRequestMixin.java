package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class TransactionRequestMixin {

    @JsonCreator
    public TransactionRequestMixin(
        @JsonProperty("issuerId") String issuerId, @JsonProperty("issuerAccount") String issuerAccount,
        @JsonProperty("transactionId") String transactionId){}


}
