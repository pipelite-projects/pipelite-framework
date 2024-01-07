package io.pipelite.core.flow.execution;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FlowExecutionDump {

    String getId();
    LocalDateTime getCreationTime();
    String getFlowHash();
    String getFlowName();
    String getSourceEndpointResource();
    void setLastExecutedProcessor(String processorName);
    String getLastExecutedProcessor();
    void setAttemptNumber(int attemptNumber);
    int getAttemptNumber();

    void setFailureException(Throwable failureException);
    Optional<Throwable> tryGetFailureException();

}
