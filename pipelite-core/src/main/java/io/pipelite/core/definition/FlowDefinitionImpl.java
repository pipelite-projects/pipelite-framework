package io.pipelite.core.definition;

import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.dsl.definition.*;
import io.pipelite.spi.flow.ExceptionHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

public class FlowDefinitionImpl implements FlowDefinition {

    private final String flowName;
    private final Collection<ProcessorDefinition> processorsDefinitions;

    private String flowHash;
    private SourceDefinition sourceDefinition;
    private EndpointDefinition endpointDefinition;
    private ExceptionHandler exceptionHandler;

    public FlowDefinitionImpl(String flowName){
        assert flowName != null : "parameter flowName is required and cannot be null.";
        this.flowName = flowName;
        this.processorsDefinitions = new ArrayList<>();
    }

    @Override
    public String getFlowName() {
        return flowName;
    }

    public String getFlowHash() {
        return flowHash;
    }

    public void setFlowHash(String flowHash) {
        this.flowHash = flowHash;
    }

    public void addProcessorDefinition(ProcessorDefinition processorDefinition){
        if(processorDefinition != null){
            processorsDefinitions.add(processorDefinition);
        }
    }

    @Override
    public int processorCount() {
        return processorsDefinitions.size();
    }

    @Override
    public Iterator<ProcessorDefinition> iterateProcessorDefinitions() {
        return processorsDefinitions.iterator();
    }

    public void setSourceDefinition(SourceDefinition sourceDefinition) {
        this.sourceDefinition = sourceDefinition;
    }

    @Override
    public SourceDefinition sourceDefinition() {
        return sourceDefinition;
    }

    @Override
    public boolean isSink() {
        return endpointDefinition instanceof SinkDefinition;
    }

    public void setEndpointDefinition(EndpointDefinition endpointDefinition) {
        this.endpointDefinition = endpointDefinition;
    }

    @Override
    public EndpointDefinition endpointDefinition() {
        return endpointDefinition;
    }

    @Override
    public SinkDefinition sinkDefinition() {
        if(isSink()){
            return (SinkDefinition) endpointDefinition;
        }
        return null;
    }

    @Override
    public <T> T getExceptionHandler(Class<T> expectedType) {
        if(exceptionHandler != null){
            if(expectedType.isAssignableFrom(exceptionHandler.getClass())){
                return expectedType.cast(exceptionHandler);
            }
            throw new ClassCastException(String.format("Unable to cast from %s to %s", exceptionHandler.getClass(), expectedType));
        }
        return null;
    }

    @Override
    public boolean isRetryable() {
        return exceptionHandler != null && exceptionHandler instanceof RetryChannelExceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler){
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowDefinitionImpl that = (FlowDefinitionImpl) o;
        return Objects.equals(flowName, that.flowName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowName);
    }
}
