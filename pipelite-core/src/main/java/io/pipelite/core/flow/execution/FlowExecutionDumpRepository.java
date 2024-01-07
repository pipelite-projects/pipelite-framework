package io.pipelite.core.flow.execution;

import java.util.Optional;

public interface FlowExecutionDumpRepository {

    Optional<FlowExecutionDump> tryLoad(String id);
    Optional<FlowExecutionDump> poll();
    void save(FlowExecutionDump flowExecutionDump);
    void remove(String id);

}
