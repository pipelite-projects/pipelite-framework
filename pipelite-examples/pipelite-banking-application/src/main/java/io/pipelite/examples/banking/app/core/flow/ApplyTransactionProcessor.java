package io.pipelite.examples.banking.app.core.flow;

import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.examples.banking.app.core.support.LogHelper;
import io.pipelite.examples.banking.domain.Account;
import io.pipelite.examples.banking.domain.Transaction;
import io.pipelite.examples.banking.domain.TransactionFactory;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.Objects;

public class ApplyTransactionProcessor implements Processor {

    protected final Logger logger = LogHelper.getBankingLogger();

    protected final TransactionFactory transactionFactory;

    public ApplyTransactionProcessor(TransactionFactory transactionFactory) {
        this.transactionFactory = Objects.requireNonNull(transactionFactory);
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final Account account = ioContext.tryGetHeaderAs(IOKeys.ACCOUNT_HEADER_NAME, Account.class)
            .orElseThrow(() -> new IllegalStateException("Unable to resolve account"));

        final TransactionMessage txMessage = ioContext.getInputPayloadAs(TransactionMessage.class);
        final String txId = txMessage.getTransactionId();

        final Transaction.Category txCategory = txMessage.getCategory();
        final BigDecimal amount = txMessage.getAmount();

        final Transaction transaction = transactionFactory.create(txId, txCategory, amount);

        account.applyTransaction(transaction, txMessage.resolveAccountRole());

        if(logger.isInfoEnabled()){
            logger.info("Transaction successfully applied [accountId: '{}', amount: {}, accountBalance: {}]",
                account.getId(), amount, account.getBalance());
        }

    }

}
