package io.pipelite.core.flow.execution.dump;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.support.hash.HashGenerator;
import io.pipelite.core.support.serialization.ObjectSerializer;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.IdentityGenerator;

public class FlowExecutionDumpFactory {

    private final IdentityGenerator identityGenerator;
    private final ObjectSerializer objectSerializer;

    public FlowExecutionDumpFactory(IdentityGenerator identityGenerator, ObjectSerializer objectSerializer) {
        Preconditions.notNull(identityGenerator, "identityGenerator is required and cannot be null");
        Preconditions.notNull(objectSerializer, "objectSerializer is required and cannot be null");
        this.identityGenerator = identityGenerator;
        this.objectSerializer = objectSerializer;
    }

    public FlowExecutionDump create(Throwable failureException, Exchange exchange){

        final String dumpId = identityGenerator.nextIdAsText();

        final String lastExecutedFlow = exchange.getPropertyOrDefault(
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME, String.class, null);
        Preconditions.notNull(lastExecutedFlow, String.format("Unable to resolve exchange property '%s'",
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME));

        final String lastExecutedFlowSourceEndpointResource = exchange.getPropertyOrDefault(
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME, String.class, null);
        Preconditions.notNull(lastExecutedFlowSourceEndpointResource, String.format("Unable to resolve exchange property '%s'",
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME));

        final String flowHash = ""; //flowDefinition.getFlowHash();

        final String lastExecutedProcessor = exchange.getPropertyOrDefault(
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME, String.class, null);
        Preconditions.notNull(lastExecutedProcessor, String.format("Unable to resolve exchange property '%s'",
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME));

        final Integer attemptNumber = exchange.getPropertyOrDefault(
            IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, Integer.class, 1);

        final SerializedFlowExecutionDump executionDump = SerializedFlowExecutionDump.createNow(dumpId, flowHash, lastExecutedFlow);
        executionDump.setFailureException(failureException);
        executionDump.setAttemptNumber(attemptNumber + 1);
        executionDump.setSourceEndpointResource(lastExecutedFlowSourceEndpointResource);
        executionDump.setLastExecutedProcessor(lastExecutedProcessor);

        final String serializedExchangeData = objectSerializer.serializeObject(exchange);
        executionDump.setExchangeData(serializedExchangeData, objectSerializer.getEncoding());

        return executionDump;
    }
}
