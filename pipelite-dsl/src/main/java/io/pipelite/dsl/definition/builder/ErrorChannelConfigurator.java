package io.pipelite.dsl.definition.builder;

import io.pipelite.dsl.definition.ErrorChannelDefinition;
import io.pipelite.dsl.definition.builder.error.ErrorChannelOperations;

public interface ErrorChannelConfigurator {

    ErrorChannelDefinition configure(ErrorChannelOperations builder);
}
