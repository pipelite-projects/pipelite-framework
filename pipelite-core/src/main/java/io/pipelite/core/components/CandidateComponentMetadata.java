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

import java.util.Objects;

public class CandidateComponentMetadata {

    private final String protocolName;
    private final Class<? extends ChannelAdapter> channelAdapterType;

    public CandidateComponentMetadata(String protocolName, Class<? extends ChannelAdapter> channelAdapterType) {
        this.protocolName = protocolName;
        this.channelAdapterType = channelAdapterType;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public Class<? extends ChannelAdapter> getChannelAdapterType() {
        return channelAdapterType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CandidateComponentMetadata that = (CandidateComponentMetadata) o;
        return Objects.equals(channelAdapterType, that.channelAdapterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelAdapterType);
    }

}
