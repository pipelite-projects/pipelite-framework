package io.pipelite.common.support;

public class StringUtils {

    public static boolean hasText(String argument) {
        return argument != null && !argument.trim().isEmpty();
    }

    public static boolean hasLength(String argument) {
        return argument != null && !argument.isEmpty();
    }
}
