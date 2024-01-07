package io.pipelite.core.definition;

import io.pipelite.dsl.definition.SinkDefinition;

public class SinkDefinitionImpl extends EndpointDefinitionImpl implements SinkDefinition {

    public SinkDefinitionImpl(String url) {
        super(url);
    }
}
