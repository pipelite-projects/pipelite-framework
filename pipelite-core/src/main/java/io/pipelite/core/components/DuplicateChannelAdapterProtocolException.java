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
package io.pipelite.core.components;

/**
 * Thrown when two {@code ChannelAdapter}s claim the same protocol (issue #57) - by {@link
 * ChannelAdapterDiscovery#discover()} when classpath scanning finds two different classes
 * registered for it in {@code META-INF/pipelite.factories}, and by {@code
 * DefaultChannelAdapterManager#registerChannelAdapter} when a caller registers one directly under
 * a name another adapter already holds. Before this, both paths resolved the collision silently -
 * a plain {@code Map#put} in the first case, {@code putIfAbsent} in the second - so which adapter
 * actually won depended on {@code HashSet} iteration order over {@code Class} objects, which is
 * not guaranteed stable across JVM runs. A protocol must be claimed by exactly one adapter; this
 * fails fast instead of leaving that ambiguous.
 */
public class DuplicateChannelAdapterProtocolException extends RuntimeException {

    public DuplicateChannelAdapterProtocolException(String protocolName, String firstClassName, String secondClassName) {
        super(String.format(
            "Protocol '%s' is claimed by more than one ChannelAdapter: %s and %s. Each protocol must be claimed by exactly one adapter.",
            protocolName, firstClassName, secondClassName));
    }

}
