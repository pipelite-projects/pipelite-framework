package io.pipelite.core.definition;

import io.pipelite.dsl.definition.EndpointDefinition;

public class EndpointDefinitionImpl implements EndpointDefinition {

    private final String url;

    public EndpointDefinitionImpl(String url) {
        this.url = url;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getFormattedUrl(){
        return url.toLowerCase().replaceAll("/", "");
    }

}
