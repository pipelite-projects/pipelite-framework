package io.pipelite.traces.process;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.trace.Traceable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OTelSpanDynamicInvocationHandler implements InvocationHandler {

    private final Map<String, Method> cachedMethods;

    private final Tracer tracer;
    private final FlowNode target;

    public OTelSpanDynamicInvocationHandler(Tracer tracer, FlowNode target) {

        this.tracer = Preconditions.notNull(tracer, "tracer is required and cannot be null");
        this.target = Preconditions.notNull(target, "target is required and cannot be null");
        this.cachedMethods = new ConcurrentHashMap<>();
        discoverMethods();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        Object invocationResult = null;

        if (cachedMethods.containsKey(method.getName())) {

            final Method targetMethod = cachedMethods.get(method.getName());

            final Span span = tracer.spanBuilder(target.getProcessorName())
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("flowName", target.getFlowName())
                .startSpan();

            try (Scope ignored = span.makeCurrent()) {

                invocationResult = targetMethod.invoke(target, args);
                span.setStatus(StatusCode.OK);

            } catch (Throwable exception) {

                span.recordException(exception);
                span.setStatus(StatusCode.ERROR, exception.getClass().getName());

            } finally {

                span.end();

            }

        } else {

            invocationResult = method.invoke(target, args);
        }

        return invocationResult;
    }

    private void discoverMethods() {

        Arrays.stream(target.getClass().getDeclaredMethods()).forEach(method -> {
            final Traceable traceableAnno = method.getAnnotation(Traceable.class);
            if (traceableAnno != null) {
                cachedMethods.put(method.getName(), method);
            }
        });
    }

}
