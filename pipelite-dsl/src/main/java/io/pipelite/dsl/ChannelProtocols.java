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
package io.pipelite.dsl;

/**
 * Names of the channel adapter protocols the framework itself relies on, defined once so no caller
 * has to repeat the literal.
 */
public final class ChannelProtocols {

    /**
     * The protocol of the queue in front of an internal flow (issue #111): {@code queue://x} is the
     * source of the flow that declares {@code fromSource("queue://x")}, and the same URL, as a
     * destination, puts an exchange on that queue. It is registered under this name by the {@code
     * queue} channel adapter in {@code META-INF/pipelite.factories}, which cannot reference this
     * constant, so a test keeps the two aligned.
     */
    public static final String QUEUE = "queue";

    private ChannelProtocols() {
    }

    /**
     * The URL of the queue named {@code queueName}: what an internal flow declares as its source
     * and what the other flows send to.
     */
    public static String queueURL(String queueName) {
        return QUEUE + "://" + queueName;
    }

}
