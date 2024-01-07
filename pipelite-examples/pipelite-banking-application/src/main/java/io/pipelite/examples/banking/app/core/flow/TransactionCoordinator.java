package io.pipelite.examples.banking.app.core.flow;

import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.examples.banking.domain.Transaction;
import io.pipelite.examples.banking.domain.TransactionFactory;
import io.pipelite.examples.banking.domain.TransactionRepository;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;

import java.math.BigDecimal;

@Deprecated
public class TransactionCoordinator extends AbstractTransactionProcessor implements Processor {

    public TransactionCoordinator(
        TransactionRepository transactionRepository, TransactionFactory transactionFactory) {
        super(transactionRepository, transactionFactory);
    }

    protected void processTxDebit(IOContext ioContext, ProcessContribution contribution) {

        final TransactionMessage txMessage = ioContext.getInputPayloadAs(TransactionMessage.class);
        final String txId = txMessage.getTransactionId();

        if(transactionRepository.exists(txId)){

            if(logger.isWarnEnabled()){
                logger.warn("A transaction with same id already exists in repository, cannot accept [txId: {}]", txId);
            }
            contribution.stopExecution();
            return;
        }

        final Transaction.Category txCategory = txMessage.getCategory();
        final BigDecimal amount = txMessage.getAmount();

        final Transaction transaction = transactionFactory.create(txId, txCategory, amount);
        transactionRepository.save(transaction);

    }

    protected void processTxCredit(IOContext ioContext, ProcessContribution contribution) {

        final TransactionMessage txMessage = ioContext.getInputPayloadAs(TransactionMessage.class);
        final String txId = txMessage.getTransactionId();

        if(!transactionRepository.exists(txId)){
            if(logger.isWarnEnabled()){
                logger.warn("Unrecognized transaction, cannot accept [txId: {}]", txId);
            }
            contribution.stopExecution();
            return;
        }

        transactionRepository.remove(txId);

    }
}
