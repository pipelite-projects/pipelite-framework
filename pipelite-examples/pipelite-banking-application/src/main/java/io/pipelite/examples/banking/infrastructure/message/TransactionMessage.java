package io.pipelite.examples.banking.infrastructure.message;

import io.pipelite.common.support.Preconditions;
import io.pipelite.examples.banking.domain.*;

import java.math.BigDecimal;
import java.util.Objects;

public class TransactionMessage {

    private final TransactionPhase transactionPhase;
    private final String transactionId;

    private final AccountId debtor;
    private final AccountId creditor;

    private Transaction.Category category;

    private BigDecimal amount;
    private String currencyCode;
    private String circuit;

    public TransactionMessage(TransactionPhase transactionPhase, String transactionId, AccountId debtor, AccountId creditor) {
        this.transactionPhase = Objects.requireNonNull(transactionPhase);
        this.transactionId = Objects.requireNonNull(transactionId);
        this.debtor = Objects.requireNonNull(debtor);
        this.creditor = Objects.requireNonNull(creditor);
    }

    public TransactionPhase getTransactionPhase() {
        return transactionPhase;
    }

    public final String getTransactionId() {
        return transactionId;
    }

    public AccountId resolveAccountId(){
        switch (transactionPhase) {
            case TRANSACTION_DEBIT -> { return debtor; }
            case TRANSACTION_CREDIT -> { return creditor; }
            default -> throw new IllegalStateException("Unable to resolve accountId");
        }
    }

    public AccountId getDebtor() {
        return debtor;
    }

    public AccountId getCreditor() {
        return creditor;
    }

    public AccountTransactionRole resolveAccountRole(){
        final AccountId accountId = resolveAccountId();
        if(debtor.equals(accountId)){
            return AccountTransactionRole.DEBTOR;
        }else if(creditor.equals(accountId)){
            return AccountTransactionRole.CREDITOR;
        }
        throw new IllegalStateException(String.format("Unrecognized accountId %s", accountId));
    }

    public Transaction.Category getCategory() {
        return category;
    }

    public void setCategory(Transaction.Category category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCircuit() {
        return circuit;
    }

    public void setCircuit(String circuit) {
        this.circuit = circuit;
    }

    public TransactionMessage forwardTransaction(){
        Preconditions.state(transactionPhase == TransactionPhase.TRANSACTION_DEBIT, String.format("Illegal action [tx-phase: '%s']", transactionPhase));
        final TransactionMessage creditTransaction = new TransactionMessage(TransactionPhase.TRANSACTION_CREDIT, transactionId, debtor, creditor);
        creditTransaction.setCategory(category);
        creditTransaction.setCircuit(circuit);
        creditTransaction.setAmount(amount);
        creditTransaction.setCurrencyCode(currencyCode);
        return creditTransaction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionMessage that = (TransactionMessage) o;
        return Objects.equals(transactionPhase, that.transactionPhase) && Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionPhase, transactionId);
    }

    @Override
    public String toString() {
        return "TransactionMessage{" +
            "transactionPhase=" + transactionPhase +
            ", transactionId='" + transactionId + '\'' +
            ", debtor=" + debtor.getAccountNumber() +
            ", creditor=" + creditor.getAccountNumber() +
            ", category=" + category +
            ", amount=" + amount +
            '}';
    }
}
