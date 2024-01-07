package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class TransactionResponseMixin {

    @JsonCreator
    public TransactionResponseMixin(@JsonProperty("transactionId") String transactionId,
                                    @JsonProperty("receiverId") String receiverId){}

}
