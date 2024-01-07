package io.pipelite.examples.banking.app.core.flow;

import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.examples.banking.domain.TransactionFactory;
import io.pipelite.examples.banking.domain.TransactionRepository;
import io.pipelite.examples.banking.domain.TransactionPhase;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTransactionProcessor implements Processor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final TransactionRepository transactionRepository;
    protected final TransactionFactory transactionFactory;

    public AbstractTransactionProcessor(TransactionRepository transactionRepository, TransactionFactory transactionFactory) {
        this.transactionRepository = transactionRepository;
        this.transactionFactory = transactionFactory;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final TransactionMessage txMessage = ioContext.getInputPayloadAs(TransactionMessage.class);
        final TransactionPhase txPhase = txMessage.getTransactionPhase();

        switch (txPhase) {
            case TRANSACTION_DEBIT -> processTxDebit(txMessage, contribution);
            case TRANSACTION_CREDIT -> processTxCredit(txMessage, contribution);
        }

    }

    protected void processTxDebit(TransactionMessage txMessage, ProcessContribution contribution){}

    protected void processTxCredit(TransactionMessage txMessage, ProcessContribution contribution){}

}
