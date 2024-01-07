package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountIdMixin {

    @JsonCreator
    public AccountIdMixin(@JsonProperty("bankId") String bankId, @JsonProperty("accountNumber") String accountNumber){}

}
