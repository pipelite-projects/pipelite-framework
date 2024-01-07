package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;

public class Deposit extends AbstractTransaction {

    public Deposit(String id, BigDecimal amount) {
        super(id, TransactionPhase.TRANSACTION_CREDIT, amount);
    }

}
