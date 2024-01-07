package io.pipelite.dsl.route;

import io.pipelite.dsl.IOContext;

public interface ConditionEvaluator {

    boolean evaluate(Condition condition, IOContext ioContext);

}
