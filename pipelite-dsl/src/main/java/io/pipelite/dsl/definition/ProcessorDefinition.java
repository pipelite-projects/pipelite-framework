package io.pipelite.dsl.definition;

public interface ProcessorDefinition {

    <T> T getProcessor(Class<T> processorType);
    String getProcessorName();
    Object getExceptionHandler();
    void setExceptionHandler(Object exceptionHandler);

}
