package io.pipelite.core.flow.execution.dump;

import io.pipelite.core.flow.execution.FlowExecutionDump;

import java.time.LocalDateTime;

public class SerializedFlowExecutionDump extends AbstractFlowExecutionDump implements FlowExecutionDump {

    private String exchangeData;
    private String encoding;

    public static SerializedFlowExecutionDump createNow(String id, String flowHash, String flowName){
        return new SerializedFlowExecutionDump(id, flowHash, flowName, LocalDateTime.now());
    }

    public SerializedFlowExecutionDump(String id, String flowHash, String flowName, LocalDateTime creationTime) {
        super(id, flowHash, flowName, creationTime);
    }

    public String getExchangeData() {
        return exchangeData;
    }

    public String getEncoding(){
        return encoding;
    }

    public void setExchangeData(String exchangeData, String encoding) {
        this.exchangeData = exchangeData;
        this.encoding = encoding;
    }
}
