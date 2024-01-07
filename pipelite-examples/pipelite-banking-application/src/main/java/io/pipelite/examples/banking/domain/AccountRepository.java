package io.pipelite.examples.banking.domain;

import java.util.Optional;

public interface AccountRepository {

    Optional<Account> tryFind(String accountId);
    boolean exists(String accountId);
    void save(Account account);

}
