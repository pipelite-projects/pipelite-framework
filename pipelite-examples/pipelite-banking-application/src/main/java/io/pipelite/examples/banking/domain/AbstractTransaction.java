package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;
import java.util.Objects;

public abstract class AbstractTransaction implements Transaction {

    private final String id;
    private final TransactionPhase phase;
    private final BigDecimal amount;

    private AccountId debtor;
    private AccountId creditor;

    public AbstractTransaction(String id, TransactionPhase phase, BigDecimal amount) {
        this.id = Objects.requireNonNull(id);
        this.phase = Objects.requireNonNull(phase);
        this.amount = Objects.requireNonNull(amount);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public TransactionPhase getPhase() {
        return phase;
    }

    @Override
    public AccountId getDebtor() {
        return debtor;
    }

    public void setDebtor(AccountId debtor) {
        this.debtor = debtor;
    }

    @Override
    public AccountId getCreditor() {
        return creditor;
    }

    public void setCreditor(AccountId creditor) {
        this.creditor = creditor;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public BigDecimal apply(BigDecimal totalAmount, AccountTransactionRole accountTransactionRole) {
        switch (phase) {
            case TRANSACTION_CREDIT -> {
                return credit(totalAmount);
            }
            case TRANSACTION_DEBIT -> {
                return debit(totalAmount);
            }
        }
        throw new IllegalStateException("Unrecognized transaction phase");
    }

    protected BigDecimal credit(BigDecimal totalAmount){
        return totalAmount.add(amount);
    }

    protected BigDecimal debit(BigDecimal totalAmount){
        if(totalAmount.compareTo(amount) >= 0){
            return totalAmount.subtract(amount);
        }
        throw new IllegalStateException(String.format("Unable to debit amount %s, insufficient funds %s on account", totalAmount, amount));
    }

}
