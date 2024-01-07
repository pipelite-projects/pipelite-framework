package io.pipelite.spi.channel.convert.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface ObjectMapperConfigurator {

    void configure(ObjectMapper objectMapper);

}
