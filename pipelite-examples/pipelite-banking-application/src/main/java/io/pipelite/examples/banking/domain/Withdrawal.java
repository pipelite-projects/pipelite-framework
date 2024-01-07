package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;

public class Withdrawal extends AbstractTransaction {

    public Withdrawal(String id, BigDecimal amount) {
        super(id, TransactionPhase.TRANSACTION_DEBIT, amount);
    }

}
