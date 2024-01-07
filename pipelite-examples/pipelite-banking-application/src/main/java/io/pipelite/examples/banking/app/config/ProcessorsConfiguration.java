package io.pipelite.examples.banking.app.config;

import io.pipelite.examples.banking.app.core.flow.*;
import io.pipelite.examples.banking.domain.AccountRepository;
import io.pipelite.examples.banking.domain.TransactionFactory;
import io.pipelite.examples.banking.domain.TransactionRepository;
import org.springframework.context.annotation.Bean;

public class ProcessorsConfiguration {

    @Bean
    public TransactionFactory transactionFactory(){
        return new TransactionFactory();
    }

    @Bean
    public AccountRepository accountRepository(){
        return new AccountRepositoryInMemoryImpl();
    }

    @Bean
    public AccountLoader accountLoader(AccountRepository accountRepository){
        return new AccountLoader(accountRepository);
    }

    @Bean
    public ApplyTransactionProcessor applyTransactionProcessor(TransactionFactory transactionFactory){
        return new ApplyTransactionProcessor(transactionFactory);
    }
}
