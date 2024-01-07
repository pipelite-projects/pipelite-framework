package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicReference;

public class Balance {

    private final Collection<TransactionHolder> transactions;

    private final BigDecimal openingAmount;

    public Balance() {
        this(BigDecimal.ZERO);
    }

    public Balance(BigDecimal openingAmount) {
        this.openingAmount = openingAmount;
        transactions = new LinkedHashSet<>();
    }

    public void applyTransaction(Transaction transaction, AccountTransactionRole accountTransactionRole){
        transactions.add(new TransactionHolder(transaction, accountTransactionRole));
    }

    public void rollbackTransaction(String transactionId){
        transactions.removeIf(txHolder -> txHolder.getTransactionId().equals(transactionId));
    }

    public BigDecimal resolveAmount() {
        final AtomicReference<BigDecimal> balanceValue = new AtomicReference<>(openingAmount);
        transactions.forEach(txHolder -> balanceValue.set(txHolder.apply(balanceValue.get())));
        return balanceValue.get();
    }

    static final class TransactionHolder {

        private final Transaction transaction;
        private final AccountTransactionRole accountTransactionRole;

        public TransactionHolder(Transaction transaction, AccountTransactionRole accountTransactionRole) {
            this.transaction = transaction;
            this.accountTransactionRole = accountTransactionRole;
        }

        public String getTransactionId(){
            return transaction.getId();
        }

        public Transaction getTransaction() {
            return transaction;
        }

        public BigDecimal apply(BigDecimal amount){
            return transaction.apply(amount, accountTransactionRole);
        }

    }

}
