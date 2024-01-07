package io.pipelite.examples.banking.app.core.flow;

import io.pipelite.examples.banking.domain.Account;
import io.pipelite.examples.banking.domain.AccountRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AccountRepositoryInMemoryImpl implements AccountRepository {

    private final Map<String, Account> repository;

    public AccountRepositoryInMemoryImpl() {
        this.repository = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Account> tryFind(String accountId) {
        return Optional.ofNullable(repository.get(accountId));
    }

    @Override
    public boolean exists(String accountId) {
        return repository.containsKey(accountId);
    }

    @Override
    public void save(Account account) {
        repository.put(account.getId(), account);
    }

}
