package io.pipelite.expression.support.conversion.support;

import io.pipelite.expression.support.conversion.impl.Converter;

public class EnumToStringConverter implements Converter<Enum<?>, String> {

    @Override
    public String convert(Enum<?> source) {
        return source.name();
    }
}
