/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
