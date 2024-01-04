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
package io.pipelite.spi.flow.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        if(logger.isErrorEnabled()){
            logger.error("An uncaught error occurred in thread {}", thread.getName(), exception);
        }
    }
}
