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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FlowExecutionDumpInMemoryRepository implements FlowExecutionDumpRepository {

    /**
     * Generous but finite, same philosophy as {@code DefaultPipeliteContext}'s shared source
     * worker pool default: protects against unbounded memory growth even with zero explicit
     * configuration, without getting in the way of normal use.
     */
    public static final int DEFAULT_MAX_SIZE = 10_000;

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Map<String, FlowExecutionDump> dumps;
    private final int maxSize;

    public FlowExecutionDumpInMemoryRepository() {
        this(DEFAULT_MAX_SIZE);
    }

    public FlowExecutionDumpInMemoryRepository(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be a positive integer, got " + maxSize);
        }
        this.maxSize = maxSize;
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
        // Reject rather than evict the oldest entry: silently dropping an existing dump to make
        // room for a new one would lose in-flight retry state exactly the same way the
        // unbounded-growth problem this cap exists to prevent would — just non-deterministically.
        if (!dumps.containsKey(flowExecutionDump.getId()) && dumps.size() >= maxSize) {
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("FlowExecutionDump repository at capacity ({} entries), rejecting dump {} for flow '{}' — " +
                        "it will be dropped instead of retried/dead-lettered",
                    maxSize, flowExecutionDump.getId(), flowExecutionDump.getFlowName());
            }
            return;
        }
        dumps.put(flowExecutionDump.getId(), flowExecutionDump);
    }

    @Override
    public void remove(String id) {
        dumps.remove(id);
    }

}
