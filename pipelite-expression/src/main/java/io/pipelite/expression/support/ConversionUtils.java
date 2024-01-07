package io.pipelite.expression.support;

import io.pipelite.common.support.Preconditions;

public class ConversionUtils {

    private ConversionUtils() {}

    public static Class<?> getEnumType(Class<?> targetType) {

        Preconditions.notNull(targetType, "targetType is required and cannot be null");
        Class<?> enumType = targetType;
        while (enumType != null && !enumType.isEnum()) {
            enumType = enumType.getSuperclass();
        }

        Preconditions.notNull(enumType, "The target type " + targetType.getName() + " does not refer to an enum");
        return enumType;
    }
}
