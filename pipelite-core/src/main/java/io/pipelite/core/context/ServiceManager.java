package io.pipelite.core.context;

import io.pipelite.spi.context.Service;

public interface ServiceManager {

    void registerService(Service service);
    void startServices();
    void stopServices();
}
