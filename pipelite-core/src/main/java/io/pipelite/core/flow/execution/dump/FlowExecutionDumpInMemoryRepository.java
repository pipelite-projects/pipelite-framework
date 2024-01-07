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
