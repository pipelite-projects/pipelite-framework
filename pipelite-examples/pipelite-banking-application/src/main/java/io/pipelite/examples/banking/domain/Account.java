package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;
import java.util.Objects;

public class Account {

    private final String id;
    private final Balance balance;

    public Account(String id, Balance balance) {
        this.id = Objects.requireNonNull(id);
        this.balance = Objects.requireNonNull(balance);
    }

    public String getId() {
        return id;
    }

    public void applyTransaction(Transaction transaction, AccountTransactionRole accountTransactionRole){
        balance.applyTransaction(transaction, accountTransactionRole);
    }

    public void rollbackTransaction(String transactionId){
        balance.rollbackTransaction(transactionId);
    }

    public BigDecimal getBalance(){
        return balance.resolveAmount();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
