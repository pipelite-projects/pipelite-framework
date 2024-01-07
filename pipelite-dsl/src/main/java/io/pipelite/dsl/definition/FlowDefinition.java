package io.pipelite.dsl.definition;

import java.util.Iterator;

public interface FlowDefinition {

    String getFlowName();

    String getFlowHash();

    int processorCount();

    Iterator<ProcessorDefinition> iterateProcessorDefinitions();

    SourceDefinition sourceDefinition();

    boolean isSink();

    EndpointDefinition endpointDefinition();

    SinkDefinition sinkDefinition();

    //Optional<ReplierDefinition> tryGetReplier();

    <T> T getExceptionHandler(Class<T> expectedType);

    boolean isRetryable();

}
