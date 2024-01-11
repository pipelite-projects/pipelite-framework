package io.pipelite.traces.flow;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.flow.process.ProcessContributionImpl;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.traces.flow.OTelProxyFlowNodePostProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OTelProxyFlowNodePostProcessorTest {

    private final ExchangeFactory exchangeFactory =
        new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));

    private OTelProxyFlowNodePostProcessor subject;

    @Before
    public void setup(){
        final TracerProvider tracerProvider = TracerProvider.noop();
        final Tracer tracer = tracerProvider.get("io.pipelite.traces.flow.OTelProxyFlowNodePostProcessorTest", "1.0");
        subject = new OTelProxyFlowNodePostProcessor(tracer, "testFlow", "testProcessor");
    }

    @Test
    public void shouldPostProcessFlowNode(){

        Processor processor = (ioContext, processContribution) -> {};
        processor = subject.postProcess(processor);

        final IOContext ioContext = exchangeFactory.createExchange(new Object());
        processor.process(ioContext, new ProcessContributionImpl());
        Assert.assertNotNull(processor);

    }
}
