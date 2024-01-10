package io.pipelite.traces.flow;

import io.opentelemetry.api.trace.Tracer;
import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.flow.FlowNodePostProcessor;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.traces.process.OTelSpanDynamicInvocationHandler;

import java.lang.reflect.Proxy;

public class OTelProxyFlowNodePostProcessor implements FlowNodePostProcessor {

    private final Tracer tracer;

    public OTelProxyFlowNodePostProcessor(Tracer tracer) {
        this.tracer = Preconditions.notNull(tracer, "tracer is required and cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends FlowNode> T postProcess(T flowNode) {
        return (T) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
            new Class<?>[]{FlowNode.class},
            new OTelSpanDynamicInvocationHandler(null, flowNode));
    }

    @Override
    public int getOrder() {
        return FlowNodePostProcessor.HIGHEST_PRECEDENCE;
    }

}
