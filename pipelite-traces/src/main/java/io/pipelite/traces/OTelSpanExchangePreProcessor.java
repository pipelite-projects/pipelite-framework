package io.pipelite.traces;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import io.pipelite.spi.flow.process.FlowExecutionContext;

public class OTelSpanExchangePreProcessor implements ExchangePreProcessor {

    private final Tracer tracer;

    public OTelSpanExchangePreProcessor(Tracer tracer) {
        this.tracer = Preconditions.notNull(tracer, "tracer is required and cannot be null");
    }

    @Override
    public void preProcess(FlowExecutionContext ctx, Exchange exchange) {
        final String flowName = ctx.getFlowName();
        final String processorName = ctx.getProcessorName();
        final Span span = tracer.spanBuilder(processorName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("flowName", flowName)
            .startSpan();

        final Scope ignored = span.makeCurrent();

    }

    @Override
    public int getOrder() {
        return ExchangePreProcessor.HIGHEST_PRECEDENCE;
    }
}
