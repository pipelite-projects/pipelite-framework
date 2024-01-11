package io.pipelite.traces.flow;

import io.opentelemetry.api.trace.Tracer;
import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.FlowNodePostProcessor;
import io.pipelite.spi.flow.ProcessorPostProcessor;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.traces.process.OTelSpanDynamicInvocationHandler;

import java.lang.reflect.Proxy;

public class OTelProxyFlowNodePostProcessor implements FlowNodePostProcessor, ProcessorPostProcessor {

    private final Tracer tracer;
    private final String flowName;
    private final String processorName;

    public OTelProxyFlowNodePostProcessor(Tracer tracer, String flowName, String processorName) {
        this.tracer = Preconditions.notNull(tracer, "tracer is required and cannot be null");
        this.flowName = Preconditions.notNull(flowName, "flowName is required and cannot be null");
        this.processorName = Preconditions.notNull(processorName, "processorName is required and cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends FlowNode> T postProcess(T flowNode) {
        return (T) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
            new Class<?>[]{FlowNode.class},
            new OTelSpanDynamicInvocationHandler(tracer, flowName, processorName, flowNode));
    }

    @Override
    public Processor postProcess(Processor processor) {
        return (Processor) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
            new Class<?>[]{Processor.class},
            new OTelSpanDynamicInvocationHandler(tracer, flowName, processorName, processor));
    }

    @Override
    public int getOrder() {
        return FlowNodePostProcessor.HIGHEST_PRECEDENCE;
    }

}
