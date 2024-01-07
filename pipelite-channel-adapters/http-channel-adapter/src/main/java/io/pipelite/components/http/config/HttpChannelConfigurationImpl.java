package io.pipelite.components.http.config;

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.channel.convert.json.ObjectMapperConfigurator;

public class HttpChannelConfigurationImpl implements HttpChannelConfiguration {

    private final RequestMappingList requestMappings;

    private ObjectMapperConfigurator objectMapperConfigurator;

    public HttpChannelConfigurationImpl() {
        this.requestMappings = new RequestMappingList();
    }

    @Override
    public void addRequestMapping(RequestMapping requestMapping) {
        Preconditions.notNull(requestMapping, "requestMapping is required and cannot be null");
        requestMappings.add(requestMapping);
    }

    @Override
    public RequestMappingList getRequestMappings() {
        return requestMappings;
    }

    @Override
    public void setObjectMapperConfigurator(ObjectMapperConfigurator objectMapperConfigurator) {
        this.objectMapperConfigurator = Preconditions.notNull(objectMapperConfigurator, "objectMapperConfigurator is required and cannot be null");
    }

    @Override
    public ObjectMapperConfigurator getObjectMapperConfigurator() {
        return objectMapperConfigurator;
    }

}
