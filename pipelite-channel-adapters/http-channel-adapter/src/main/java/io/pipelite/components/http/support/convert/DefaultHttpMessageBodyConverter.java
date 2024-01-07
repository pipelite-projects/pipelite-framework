package io.pipelite.components.http.support.convert;

import io.pipelite.common.util.MimeType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultHttpMessageBodyConverter implements HttpMessageBodyConverter {

    private final Collection<HttpMessageBodyConverter> converters;

    public DefaultHttpMessageBodyConverter() {
        this.converters = new ArrayList<>();
    }

    public void addConverter(HttpMessageBodyConverter converter){
        converters.add(converter);
    }

    @Override
    public boolean supports(MimeType contentType) {
        return converters.stream()
            .anyMatch(converter -> converter.supports(contentType));
    }

    @Override
    public <T> Object convert(byte[] requestBody, MimeType contentType, Class<T> expectedType) {

        final Optional<HttpMessageBodyConverter> httpMessageBodyConverterHolder = converters.stream()
            .filter(converter -> converter.supports(contentType))
            .findFirst();

        if(httpMessageBodyConverterHolder.isPresent()){
            final HttpMessageBodyConverter httpMessageBodyConverter = httpMessageBodyConverterHolder.get();
            return httpMessageBodyConverter.convert(requestBody, contentType, expectedType);
        }

        try(final BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(requestBody), StandardCharsets.UTF_8))){
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new HttpMessageBodyConversionException("An underlying error occurred converting Http message request body", e);
        }

    }
}
