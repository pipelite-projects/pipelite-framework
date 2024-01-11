package io.pipelite.traces.process;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.pipelite.common.support.Preconditions;
import io.pipelite.common.trace.Traceable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OTelSpanDynamicInvocationHandler implements InvocationHandler {

    private final Map<String, Method> cachedMethods;

    private final Tracer tracer;
    private final String flowName;
    private final String processorName;
    private final Object target;

    public OTelSpanDynamicInvocationHandler(Tracer tracer, String flowName, String processorName, Object target) {

        this.tracer = Preconditions.notNull(tracer, "tracer is required and cannot be null");
        this.flowName = Preconditions.notNull(flowName, "flowName is required and cannot be null");
        this.processorName = Preconditions.notNull(processorName, "processorName is required and cannot be null");
        this.target = Preconditions.notNull(target, "target is required and cannot be null");
        this.cachedMethods = new ConcurrentHashMap<>();
        discoverMethods();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        Object invocationResult = null;

        if (cachedMethods.containsKey(method.getName())) {

            final Method targetMethod = cachedMethods.get(method.getName());

            final Span span = tracer.spanBuilder(processorName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("flowName", flowName)
                .setAttribute("processorName", processorName)
                .startSpan();

            try (Scope ignored = span.makeCurrent()) {

                invocationResult = targetMethod.invoke(target, args);
                span.setStatus(StatusCode.OK);

            } catch (InvocationTargetException exception) {

                final Throwable targetException = exception.getTargetException();
                span.recordException(targetException);
                span.setStatus(StatusCode.ERROR, targetException.getClass().getName());

                throw targetException;

            } finally {

                span.end();

            }

        } else {

            invocationResult = method.invoke(target, args);
        }

        return invocationResult;
    }

    private void discoverMethods() {

        final Class<?> targetClass = target.getClass();
        Arrays.stream(targetClass.getInterfaces()).forEach(iface -> {
            Arrays.stream(iface.getDeclaredMethods()).forEach(method -> {
                final Traceable traceableAnno = method.getAnnotation(Traceable.class);
                if (traceableAnno != null) {
                    cachedMethods.put(method.getName(), method);
                }
            });
        });
    }

}
