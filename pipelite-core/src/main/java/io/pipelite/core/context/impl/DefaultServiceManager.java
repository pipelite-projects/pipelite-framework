package io.pipelite.core.context.impl;

import io.pipelite.core.context.ServiceManager;
import io.pipelite.spi.context.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultServiceManager implements ServiceManager {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Map<String, Service> services;

    public DefaultServiceManager() {
        this.services = new ConcurrentHashMap<>();
    }

    @Override
    public void registerService(Service service) {

        Objects.requireNonNull(service, "service is required and cannot be null");
        final String serviceClassName = service.getClass().getCanonicalName();

        final long serviceClassIdx = services
            .keySet()
            .stream()
            .filter(k -> k.startsWith(serviceClassName))
            .count();

        final String serviceKey = String.format("%s-%s", serviceClassName, serviceClassIdx);
        services.put(serviceKey, service);

    }

    @Override
    public void startServices() {
        services.forEach((className, service) -> {
            service.start();
            if(sysLogger.isDebugEnabled()){
                sysLogger.debug("Service {} successfully started", className);
            }
        });
    }

    @Override
    public void stopServices() {
        services.forEach((className, service) -> {
            service.stop();
            if(sysLogger.isDebugEnabled()){
                sysLogger.debug("Service {} successfully stopped", className);
            }
        });
    }
}
