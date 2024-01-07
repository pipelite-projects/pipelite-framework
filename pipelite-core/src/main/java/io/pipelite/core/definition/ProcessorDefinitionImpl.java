package io.pipelite.core.definition;

import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.dsl.definition.ProcessorDefinition;

public class ProcessorDefinitionImpl implements ProcessorDefinition {

    private final String processorName;
    private final FlowNode flowNode;

    private Object exceptionHandler;

    public ProcessorDefinitionImpl(String processorName, FlowNode flowNode) {
        this.processorName = processorName;
        this.flowNode = flowNode;
    }

    @Override
    public <T> T getProcessor(Class<T> processorType) {
        if(processorType.isAssignableFrom(flowNode.getClass())){
            return processorType.cast(flowNode);
        }
        throw new ClassCastException(String.format("Unable to cast from %s to %s", flowNode.getClass(), processorType));
    }

    @Override
    public String getProcessorName() {
        return processorName;
    }

    @Override
    public Object getExceptionHandler() {
        return exceptionHandler;
    }

    @Override
    public void setExceptionHandler(Object exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
}
