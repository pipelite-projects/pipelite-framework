package io.pipelite.dsl.process;

public interface ProcessContribution {

    void stopExecution();
    boolean isExecutionStopped();
}
