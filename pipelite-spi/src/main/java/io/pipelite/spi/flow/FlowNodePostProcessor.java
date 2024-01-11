package io.pipelite.spi.flow;

import io.pipelite.spi.flow.exchange.FlowNode;

public interface FlowNodePostProcessor extends PostProcessor {

    <T extends FlowNode> T postProcess(T flowNode);

}
