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

import io.pipelite.spi.channel.ChannelAdapter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only public entry point into this package - part of the pre-v1.0.0 audit's Tier 4 #12
 * lock-down (issue #82). {@code CandidateChannelAdapterResolver}, {@code ChannelAdapterFactory}
 * and {@code CandidateComponentMetadata} are all package-private: {@code
 * DefaultChannelAdapterManager} only ever needed the resolve-then-instantiate result, a
 * protocol name to already-public {@link ChannelAdapter} mapping, never the classpath-scanning
 * machinery itself.
 */
public class ChannelAdapterDiscovery {

    private final CandidateChannelAdapterResolver resolver = new CandidateChannelAdapterResolver();
    private final ChannelAdapterFactory factory = new ChannelAdapterFactory();

    public Map<String, ChannelAdapter> discover() {
        final Map<String, ChannelAdapter> discovered = new LinkedHashMap<>();
        resolver.findCandidates().forEach(candidate ->
            discovered.put(candidate.getProtocolName(), factory.instantiateAdapter(candidate.getChannelAdapterType())));
        return discovered;
    }

}
