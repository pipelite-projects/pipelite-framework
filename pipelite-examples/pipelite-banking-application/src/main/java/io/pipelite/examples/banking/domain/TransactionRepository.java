package io.pipelite.examples.banking.domain;

import java.util.Optional;

@Deprecated
public interface TransactionRepository {

    Optional<Transaction> tryFind(String transactionId);
    boolean exists(String transactionId);
    void save(Transaction transaction);
    void remove(String transactionId);

}
