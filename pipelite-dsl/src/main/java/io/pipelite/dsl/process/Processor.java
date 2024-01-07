package io.pipelite.dsl.process;

import io.pipelite.dsl.IOContext;

public interface Processor {

    void process(IOContext ioContext, ProcessContribution contribution);

}
