package io.pipelite.examples.banking.domain;

import java.util.Objects;

public class AccountId {

    private final String bankId;
    private final String accountNumber;

    public AccountId(String bankId, String accountNumber) {
        this.bankId = Objects.requireNonNull(bankId);
        this.accountNumber = Objects.requireNonNull(accountNumber);
    }

    public String getBankId() {
        return bankId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    @Override
    public String toString() {
        return String.format("[bankId: '%s', accountNumber: '%s']", bankId, accountNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountId accountId1 = (AccountId) o;
        return Objects.equals(bankId, accountId1.bankId) && Objects.equals(accountNumber, accountId1.accountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bankId, accountNumber);
    }
}
