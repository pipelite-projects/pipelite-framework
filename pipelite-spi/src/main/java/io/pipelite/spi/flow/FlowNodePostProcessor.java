package io.pipelite.spi.flow;

import io.pipelite.spi.flow.exchange.FlowNode;

public interface FlowNodePostProcessor {

    int LOWEST_PRECEDENCE = Integer.MIN_VALUE;
    int HIGHEST_PRECEDENCE = Integer.MAX_VALUE;

    <T extends FlowNode> T postProcess(T flowNode);

    default int getOrder(){
        return 0;
    }

}
