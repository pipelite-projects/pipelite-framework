package io.pipelite.examples.banking.domain;

import java.math.BigDecimal;

public class TransactionFactory {

    public Transaction create(String transactionId, Transaction.Category category, BigDecimal amount){

        switch (category) {
            case DEPOSIT -> {
                return new Deposit(transactionId, amount);
            }
            case PAYMENT -> {
                return new Payment(transactionId, amount);
            }
            case WITHDRAWAL -> {
                return new Withdrawal(transactionId, amount);
            }
        }
        throw new IllegalArgumentException(String.format("Unrecognized transaction category '%s'", category));
    }
}
