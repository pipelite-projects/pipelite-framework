package io.pipelite.spi.flow;

import io.pipelite.dsl.process.Processor;

public interface ProcessorPostProcessor extends PostProcessor {

    Processor postProcess(Processor processor);

}
