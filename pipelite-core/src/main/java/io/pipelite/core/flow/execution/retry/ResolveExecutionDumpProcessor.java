package io.pipelite.core.flow.execution.retry;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;

public class ResolveExecutionDumpProcessor implements Processor {

    private final FlowExecutionDumpRepository dumpRepository;

    public ResolveExecutionDumpProcessor(FlowExecutionDumpRepository dumpRepository) {
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");
        this.dumpRepository = dumpRepository;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        final Exchange exchange = (Exchange)ioContext;

        FlowExecutionDump flowExecutionDump = exchange.getInputPayloadAs(FlowExecutionDump.class);

        if(flowExecutionDump == null){

            final String executionDumpId = exchange.getProperty(IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME, String.class);
            Preconditions.notNull(executionDumpId, String.format("Exchange property %s is required and cannot be null",
                IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME));

            flowExecutionDump = dumpRepository.tryLoad(executionDumpId)
                .orElseThrow(() -> new IllegalStateException(String.format("Unrecognized flow-execution-dump id '%s'", executionDumpId)));
            ioContext.setOutputPayload(flowExecutionDump);

        }

        dumpRepository.remove(flowExecutionDump.getId());

    }

}
