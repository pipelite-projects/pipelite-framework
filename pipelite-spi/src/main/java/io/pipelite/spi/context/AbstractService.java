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
package io.pipelite.spi.context;

public abstract class AbstractService implements Service {

    private byte status;

    public AbstractService() {
        this.status = Service.STATUS_INIT;
    }

    @Override
    public void start() {
        status = Service.STATUS_STARTING;
        doStart();
        status = Service.STATUS_STARTED;
    }

    @Override
    public void stop() {
        status = Service.STATUS_STOPPING;
        doStop();
        status = Service.STATUS_STOPPED;
    }

    protected boolean isRunAllowed(){
        return status >= Service.STATUS_STARTING
            && status <= Service.STATUS_STARTED;
    }

    public abstract void doStart();
    public abstract void doStop();

    public int getStatus(){
        return status;
    }
}
