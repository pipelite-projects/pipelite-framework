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

import java.io.Closeable;
import java.io.IOException;

public interface Service extends Closeable {

    byte STATUS_INIT = 0;
    byte STATUS_STARTING = 1;
    byte STATUS_STARTED = 2;
    byte STATUS_STOPPING = 3;
    byte STATUS_STOPPED = 4;

    default void start(){}
    default void stop(){}

    default void close() throws IOException {
        try {
            stop();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}
