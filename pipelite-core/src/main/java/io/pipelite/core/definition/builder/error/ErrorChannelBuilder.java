package io.pipelite.core.definition.builder.error;

import io.pipelite.dsl.definition.builder.error.DefinedErrorChannelOperations;
import io.pipelite.dsl.definition.builder.error.ErrorChannelOperations;

public class ErrorChannelBuilder implements ErrorChannelOperations, DefinedErrorChannelOperations {

    @Override
    public DefinedErrorChannelOperations definedChannel(String channelName) {
        return null;
    }

}
