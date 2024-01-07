/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pipelite.components.http.undertow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipelite.common.support.Preconditions;
import io.pipelite.common.util.MimeType;
import io.pipelite.common.util.MimeTypeUtils;
import io.pipelite.components.http.HttpChannelAdapter;
import io.pipelite.components.http.config.HttpChannelConfiguration;
import io.pipelite.components.http.config.RequestMapping;
import io.pipelite.components.http.config.RequestMappingList;
import io.pipelite.components.http.config.RequestMethod;
import io.pipelite.components.http.support.convert.DefaultHttpMessageBodyConverter;
import io.pipelite.components.http.support.convert.HttpMessageBodyJsonConverter;
import io.pipelite.spi.channel.convert.json.ObjectMapperConfigurator;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Optional;

public class DefaultHttpHandler implements HttpHandler, ExchangeFactoryAware {

    private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";

    private static final MimeType DEFAULT_CONTENT_TYPE = MimeTypeUtils.parseMimeType("text/plain");

    private Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpChannelAdapter component;
    private final RequestMappingList requestMappings;

    private final DefaultHttpMessageBodyConverter requestBodyConverter;

    private ExchangeFactory exchangeFactory;

    public DefaultHttpHandler(HttpChannelAdapter component, HttpChannelConfiguration configuration) {

        this.component = Preconditions.notNull(component, "component is required and cannot be null");

        Preconditions.notNull(configuration, "configuration is required and cannot be null");
        this.requestMappings = configuration.getRequestMappings();

        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectMapperConfigurator objectMapperConfigurator = configuration.getObjectMapperConfigurator();
        objectMapperConfigurator.configure(objectMapper);

        this.requestBodyConverter = new DefaultHttpMessageBodyConverter();
        this.requestBodyConverter.addConverter(new HttpMessageBodyJsonConverter(objectMapper));

    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws IOException {

        final String requestedResource = httpServerExchange.getRequestURI().replaceFirst("/", "");
        final Consumer consumer = component.tryResolveConsumer(requestedResource).orElseThrow(() ->
            new IllegalStateException(String.format("Unrecognized requestURI, unable to resolve exchange consumer [requestURI: %s]",
                httpServerExchange.getRequestURI())));

        final HttpString httpRequestMethod = httpServerExchange.getRequestMethod();
        if(!RequestMethod.isAllowed(httpRequestMethod.toString())){
            endHttpExchange(httpServerExchange, 400);
            return;
        }

        try(BlockingHttpExchange blockingHttpExchange = httpServerExchange.startBlocking()) {

            final InputStream requestBodyStream = blockingHttpExchange.getInputStream();

            byte[] requestBodyContent = new byte[requestBodyStream.available()];
            int ignored = requestBodyStream.read(requestBodyContent);

            final MimeType contentType = resolveMimeType(httpServerExchange);

            final RequestMethod requestMethod = RequestMethod.fromValue(httpRequestMethod.toString());

            Class<?> payloadType = requestMappings
                .tryFindBestMatch(requestedResource, requestMethod, contentType)
                .map(RequestMapping::getPayloadType)
                .orElse(null);
            if (payloadType == null) {
                payloadType = Object.class;
            }

            final Object requestPayload = requestBodyConverter.convert(requestBodyContent, contentType, payloadType);

            final Exchange exchange = exchangeFactory.createExchange(requestPayload);
            consumer.consume(exchange);

            endHttpExchange(httpServerExchange, 201);

        } catch (RuntimeException exception){

            if(logger.isErrorEnabled()){
                logger.error("An underlying error occurred handling HTTP request", exception);
            }
            throw exception;
        }

    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    private MimeType resolveMimeType(HttpServerExchange httpServerExchange){
        final HeaderMap httpHeaders =  httpServerExchange.getRequestHeaders();
        final HeaderValues headerValues = httpHeaders.get(CONTENT_TYPE_HEADER_NAME);
        if(headerValues != null){
            final String contentTypeText = headerValues.getFirst();
            return MimeTypeUtils.parseMimeType(contentTypeText);
        }
        return DEFAULT_CONTENT_TYPE;
    }

    private static void endHttpExchange(HttpServerExchange httpServerExchange, int statusCode){
        if(!httpServerExchange.isResponseStarted()){
            httpServerExchange.setStatusCode(statusCode);
        }
        httpServerExchange.endExchange();
    }

}
