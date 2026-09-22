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

import java.util.Collection;
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

    /**
     * Fails fast (issue #57) the moment two different classes claim the same protocol in {@code
     * META-INF/pipelite.factories} - a plain {@code Map#put} used to let the second one silently
     * overwrite the first, and since {@code findCandidates()} returns a {@code Set} with no
     * defined iteration order over the colliding entries, which one actually won was not even
     * stable across JVM runs. Two identical candidates (same protocol, same class - e.g. the same
     * factories entry reachable through more than one classpath resource URL) are not a collision:
     * {@code CandidateComponentMetadata}'s own {@code equals}/{@code hashCode} already collapse
     * those upstream, in {@code findCandidates()}'s own {@code Set}.
     */
    public Map<String, ChannelAdapter> discover() {
        return instantiate(resolver.findCandidates());
    }

    /**
     * Split out from {@link #discover()} so the collision check has something other than a real
     * classpath scan to be tested against - constructing {@link CandidateComponentMetadata}
     * directly is enough, no {@code META-INF/pipelite.factories} resource involved.
     */
    Map<String, ChannelAdapter> instantiate(Collection<CandidateComponentMetadata> candidates) {
        final Map<String, Class<? extends ChannelAdapter>> claimedBy = new LinkedHashMap<>();
        final Map<String, ChannelAdapter> discovered = new LinkedHashMap<>();
        candidates.forEach(candidate -> {
            final String protocolName = candidate.getProtocolName();
            final Class<? extends ChannelAdapter> channelAdapterType = candidate.getChannelAdapterType();
            final Class<? extends ChannelAdapter> alreadyClaimedBy = claimedBy.putIfAbsent(protocolName, channelAdapterType);
            if (alreadyClaimedBy != null && !alreadyClaimedBy.equals(channelAdapterType)) {
                throw new DuplicateChannelAdapterProtocolException(protocolName, alreadyClaimedBy.getName(), channelAdapterType.getName());
            }
            discovered.put(protocolName, factory.instantiateAdapter(channelAdapterType));
        });
        return discovered;
    }

}
