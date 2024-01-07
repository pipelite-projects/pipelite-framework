package io.pipelite.core.flow;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpFactory;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryChannelExceptionHandler implements ExceptionHandler {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private FlowExecutionDumpFactory executionDumpFactory;
    private FlowExecutionDumpRepository dumpRepository;

    public RetryChannelExceptionHandler() {
    }

    public void setExecutionDumpFactory(FlowExecutionDumpFactory executionDumpFactory) {
        if(this.executionDumpFactory == null){
            this.executionDumpFactory = executionDumpFactory;
        }
    }

    public void setDumpRepository(FlowExecutionDumpRepository dumpRepository) {
        if(this.dumpRepository == null) {
            this.dumpRepository = dumpRepository;
        }
    }

    @Override
    public void handleException(Throwable failureException, Exchange exchange) {

        Preconditions.notNull(executionDumpFactory, "executionDumpFactory is required and cannot be null");
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");

        exchange.putHeader(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME, failureException.getClass());
        exchange.putHeader(IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME, failureException.getMessage());

        final int attemptNumber = exchange.getPropertyOrDefault(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, Integer.class, 1);

        final FlowExecutionDump executionDump = executionDumpFactory.create(failureException, exchange);
        executionDump.setAttemptNumber(attemptNumber);

        final String executionDumpId = executionDump.getId();
        exchange.setProperty(IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME, executionDumpId);

        dumpRepository.save(executionDump);

    }

}
