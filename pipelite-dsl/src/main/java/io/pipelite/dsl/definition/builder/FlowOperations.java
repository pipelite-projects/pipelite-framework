package io.pipelite.dsl.definition.builder;

public interface FlowOperations extends SourceOperations, ProcessOperations,
    SinkOperations, BuildOperations {

    SourceOperations fromSource(String url);

}
