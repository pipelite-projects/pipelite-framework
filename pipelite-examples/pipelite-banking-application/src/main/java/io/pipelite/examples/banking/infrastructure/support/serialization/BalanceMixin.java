package io.pipelite.examples.banking.infrastructure.support.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pipelite.examples.banking.domain.Balance;

import java.math.BigDecimal;

public class BalanceMixin {

    @JsonCreator
    public BalanceMixin(@JsonProperty("openingAmount")BigDecimal openingAmount){}

}
