package io.pipelite.core.flow;

import java.util.regex.Pattern;

public class ExpressionVariables {

    public static final String PAYLOAD_VARIABLE_NAME = "Payload";
    public static final String HEADERS_VARIABLE_NAME = "Headers";

    public static final String EXPRESSION_START_DELIMITER = "#{";
    public static final String EXPRESSION_END_DELIMITER = "}";

    public static final Pattern EXPRESSION_PATTERN = Pattern.compile("#\\{([^{}]+)\\}");

}
