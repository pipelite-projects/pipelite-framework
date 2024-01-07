package io.pipelite.examples.banking.app.config;

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.examples.banking.app.core.flow.AccountLoader;
import io.pipelite.examples.banking.app.core.flow.ApplyTransactionProcessor;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import(ProcessorsConfiguration.class)
public class FlowConfiguration {

    @Value("${application.bank-id}")
    private String bankId;

    @Bean
    public FlowDefinition paymentFlow(PipeliteContext context) {

        return Pipelite.defineFlow("payment-channel")
            .fromSource("http://banking-payment-channel")
            .toSink(String.format("kafka://banking.%s.tx.channel", bankId))
            .build();
    }

    @Bean
    public FlowDefinition applyTransactionFlow(
        AccountLoader accountLoader, ApplyTransactionProcessor applyTransactionProcessor) {

        return Pipelite.defineFlow("transaction-apply-channel")
            .fromSource(String.format("kafka://banking.%s.tx.channel", bankId))
            .process("load-account", accountLoader)
            .process("apply-tx", applyTransactionProcessor)
            .toRoute(routes ->
                routes.dynamicRouting()
                    .when("Payload.transactionPhase eq 'TRANSACTION_DEBIT'").then("link://forward-transaction", "slf4j://tx-debit")
                    .when("Payload.transactionPhase eq 'TRANSACTION_CREDIT'").then("slf4j://tx-credit")
                    .end()
            )
            .build();

    }

    @Bean
    public FlowDefinition nextTransactionPhaseFlow(){
        return Pipelite.defineFlow("forward-transaction-channel")
            .fromSource("forward-transaction")
            .transformPayload("forward-transaction-message", inputPayload ->
                inputPayload.getPayloadAs(TransactionMessage.class).forwardTransaction())
            .toRoute(routes ->
                routes.staticRouting()
                    .recipientList("kafka://banking.#{Payload.creditor.bankId}.tx.channel")
                    .end()
            )
            .build();
    }

    @Bean
    public FlowDefinition transactionRollbackFlow(PipeliteContext context) {

        return Pipelite.defineFlow("transaction-rollback-channel")
            .fromSource(String.format("kafka://banking.%s.tx-rollback.channel", bankId))
            .toSink("slf4j://transactions?level=INFO")
            .build();

    }

}
