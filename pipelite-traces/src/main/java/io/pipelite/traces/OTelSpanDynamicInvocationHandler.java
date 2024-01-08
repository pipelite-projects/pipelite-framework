package io.pipelite.traces;

import io.pipelite.common.support.Preconditions;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class OTelSpanDynamicInvocationHandler implements InvocationHandler {



    private final Object target;

    public OTelSpanDynamicInvocationHandler(Object target) {
        this.target = Preconditions.notNull(target, "target is required and cannot be null");
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        return null;
    }

}
