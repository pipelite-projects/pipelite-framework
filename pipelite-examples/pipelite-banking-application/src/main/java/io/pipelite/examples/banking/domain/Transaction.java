package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;

public interface Transaction {

    enum Category {
        DEPOSIT, PAYMENT, WITHDRAWAL;
    }

    String getId();
    TransactionPhase getPhase();
    BigDecimal getAmount();

    AccountId getDebtor();
    AccountId getCreditor();

    BigDecimal apply(BigDecimal amount, AccountTransactionRole accountTransactionRole);

}
