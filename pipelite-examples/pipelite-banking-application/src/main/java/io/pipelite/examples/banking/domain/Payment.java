package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;

public class Payment extends AbstractTransaction {

    public Payment(String id, BigDecimal amount) {
        super(id, TransactionPhase.TRANSACTION_DEBIT, amount);
    }

    @Override
    public BigDecimal apply(BigDecimal totalAmount, AccountTransactionRole accountTransactionRole) {
        switch (accountTransactionRole) {
            case CREDITOR -> {
                return credit(totalAmount);
            }
            case DEBTOR -> {
                return debit(totalAmount);
            }
        }
        throw new IllegalStateException("Unrecognized transaction phase");
    }
}
