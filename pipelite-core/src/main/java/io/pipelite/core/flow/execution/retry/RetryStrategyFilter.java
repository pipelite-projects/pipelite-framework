package io.pipelite.core.flow.execution.retry;

import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;

public class RetryStrategyFilter implements Processor {

    private final int MAX_ATTEMPTS = 3;

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final FlowExecutionDump executionDump = ioContext.getInputPayloadAs(FlowExecutionDump.class);
        if(executionDump.getAttemptNumber() > MAX_ATTEMPTS){
            contribution.stopExecution();
        }

    }

}
