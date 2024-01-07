package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pipelite.examples.banking.domain.Balance;

public class AccountMixin {

    @JsonCreator
    public AccountMixin(@JsonProperty("id") String id, @JsonProperty("balance") Balance balance){}

}
