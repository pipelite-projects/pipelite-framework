package io.pipelite.core.flow.process;

import io.pipelite.dsl.process.ProcessContribution;

public class ProcessContributionImpl implements ProcessContribution {

    private boolean isExecutionStopped = false;

    @Override
    public void stopExecution() {
        isExecutionStopped = true;
    }

    @Override
    public boolean isExecutionStopped() {
        return isExecutionStopped;
    }
}
