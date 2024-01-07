package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pipelite.examples.banking.domain.AccountId;
import io.pipelite.examples.banking.domain.TransactionPhase;

public class TransactionMessageMixin {

    @JsonCreator
    public TransactionMessageMixin(@JsonProperty("transactionPhase") TransactionPhase transactionPhase, @JsonProperty("transactionId") String transactionId,
                                   @JsonProperty("debtor") AccountId debtor, @JsonProperty("creditor") AccountId creditor) {}
}
