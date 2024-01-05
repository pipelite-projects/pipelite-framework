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
package io.pipelite.core.flow.execution.dump;

import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.spi.flow.process.ExchangePostProcessor;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FlowExecutionDumpInMemoryRepository implements FlowExecutionDumpRepository {

    private final Map<String, FlowExecutionDump> dumps;

    public FlowExecutionDumpInMemoryRepository() {
        this.dumps = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<FlowExecutionDump> tryLoad(String id) {
        return Optional.ofNullable(dumps.get(id));
    }

    @Override
    public Optional<FlowExecutionDump> poll() {
        return dumps.values()
            .stream()
            .min(Comparator.comparing(FlowExecutionDump::getCreationTime));
    }

    @Override
    public void save(FlowExecutionDump flowExecutionDump) {
        dumps.put(flowExecutionDump.getId(), flowExecutionDump);
    }

    @Override
    public void remove(String id) {
        dumps.remove(id);
    }

}
