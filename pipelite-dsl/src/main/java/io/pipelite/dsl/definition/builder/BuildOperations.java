package io.pipelite.dsl.definition.builder;

public interface BuildOperations extends EndOperations {

    EndOperations withRetryChannel();
    EndOperations withErrorChannel(ErrorChannelConfigurator configurator);

}
