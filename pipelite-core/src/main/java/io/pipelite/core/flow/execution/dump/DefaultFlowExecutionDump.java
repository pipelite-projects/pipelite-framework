package io.pipelite.core.flow.execution.dump;

import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.spi.flow.exchange.Exchange;

import java.time.LocalDateTime;

public class DefaultFlowExecutionDump extends AbstractFlowExecutionDump implements FlowExecutionDump {

    private Exchange exchange;

    public DefaultFlowExecutionDump(String id, String flowHash, String flowName, LocalDateTime creationTime) {
        super(id, flowHash, flowName, creationTime);
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }
}
