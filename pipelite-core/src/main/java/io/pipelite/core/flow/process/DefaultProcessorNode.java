package io.pipelite.core.flow.process;

import io.pipelite.dsl.process.Processor;

public class DefaultProcessorNode extends AbstractProcessorNode {

    public DefaultProcessorNode(Processor delegate) {
        super(delegate);
    }

}
