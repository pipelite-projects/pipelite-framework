package io.pipelite.examples.banking.app.core.flow;

import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.examples.banking.domain.Account;
import io.pipelite.examples.banking.domain.AccountId;
import io.pipelite.examples.banking.domain.AccountRepository;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;

import java.util.Optional;

public class AccountLoader implements Processor {

    private final AccountRepository accountRepository;

    public AccountLoader(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final TransactionMessage txMessage = ioContext.getInputPayloadAs(TransactionMessage.class);
        final AccountId accountId = txMessage.resolveAccountId();
        final Optional<Account> accountHolder = accountRepository.tryFind(accountId.getAccountNumber());
        if(accountHolder.isEmpty()){
            throw new IllegalArgumentException(String.format("Unrecognized account, id '%s'", accountId));
        }

        final Account account = accountHolder.get();
        ioContext.putHeader(IOKeys.ACCOUNT_HEADER_NAME, account);

    }
}
