package io.pipelite.components.http.config;

import io.pipelite.spi.channel.convert.json.ObjectMapperConfigurator;

public interface HttpChannelConfiguration {

    void addRequestMapping(RequestMapping requestMapping);
    RequestMappingList getRequestMappings();
    void setObjectMapperConfigurator(ObjectMapperConfigurator objectMapperConfigurator);
    ObjectMapperConfigurator getObjectMapperConfigurator();

}
