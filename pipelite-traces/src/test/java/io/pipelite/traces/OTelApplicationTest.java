package io.pipelite.traces;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.flow.process.ProcessContributionImpl;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.traces.flow.OTelProxyFlowNodePostProcessor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_VERSION;

@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {OTelApplicationTest.ApplicationConfiguration.class})
public class OTelApplicationTest {

    @Autowired
    private OTelProxyFlowNodePostProcessor subject;

    private final ExchangeFactory exchangeFactory =
        new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));

    @Test
    public void shouldStart(){

        Assert.assertNotNull(subject);

        Processor processor = (ioContext, processContribution) -> {};
        processor = subject.postProcess(processor);

        final IOContext ioContext = exchangeFactory.createExchange(new Object());
        processor.process(ioContext, new ProcessContributionImpl());

        Assert.assertNotNull(processor);

    }

    @Configuration
    public static class ApplicationConfiguration {

        @Bean
        public OpenTelemetry openTelemetry() {

            final Resource resource = Resource.getDefault()
                .toBuilder()
                .put(SERVICE_NAME, "test-server")
                .put(SERVICE_VERSION, "0.1.0")
                .build();

            final SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().build()).build())
                .setResource(resource)
                .build();

            return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        }

        @Bean
        public OTelProxyFlowNodePostProcessor postProcessor(OpenTelemetry openTelemetry){
            final TracerProvider tracerProvider = openTelemetry.getTracerProvider();
            final Tracer tracer = tracerProvider.get("OTelProxyFlowNodePostProcessor", "1.0");
            return new OTelProxyFlowNodePostProcessor(tracer, "testFlow", "testProcessor");
        }
    }

}
