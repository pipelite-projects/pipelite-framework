package io.pipelite.examples.banking.app.core.flow;

import io.pipelite.examples.banking.domain.Transaction;
import io.pipelite.examples.banking.domain.TransactionRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionRepositoryInMemoryImpl implements TransactionRepository {

    private final Map<String,Transaction> repository;

    public TransactionRepositoryInMemoryImpl() {
        this.repository = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Transaction> tryFind(String transactionId) {
        return Optional.ofNullable(repository.get(transactionId));
    }

    @Override
    public boolean exists(String transactionId) {
        return repository.containsKey(transactionId);
    }

    @Override
    public void save(Transaction transaction) {
        repository.put(transaction.getId(), transaction);
    }

    @Override
    public void remove(String transactionId) {
        repository.remove(transactionId);
    }

}
