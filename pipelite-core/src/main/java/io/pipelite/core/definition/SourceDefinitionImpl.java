package io.pipelite.core.definition;

import io.pipelite.dsl.definition.SourceDefinition;

public class SourceDefinitionImpl extends EndpointDefinitionImpl implements SourceDefinition {

    public SourceDefinitionImpl(String url) {
        super(url);
    }

}
