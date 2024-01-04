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
package io.pipelite.expression.support;

import io.pipelite.common.support.Preconditions;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple utility class for working with the reflection API and handling reflection exceptions.
 *
 * <p>
 * Only intended for internal use.
 *
 */
public class ReflectionUtils {
    private static final String CLASS_NOT_BE_NULL = "Class must not be null";

    private ReflectionUtils() {
        super();
    }

    public static PropertyDescriptor findProperty(Class<?> clazz, String propertyName) {
        try {
            return new PropertyDescriptor(propertyName, clazz);
        }
        catch (IntrospectionException e) {
            handleInvocationTargetException(new InvocationTargetException(e));
        }
        return null;
    }

    /**
     * Attempt to find a {@link Method} on the supplied class with the supplied name and no parameters. Searches all
     * superclasses up to {@code Object}. Returns {@code null} if no {@link Method} can be found.
     * 
     * @param clazz the class to introspect
     * @param name the name of the method
     * @return the Method object, or {@code null} if none found
     */
    public static Method findMethod(Class<?> clazz, String name) {
        return findMethod(clazz, name, new Class<?>[0]);
    }

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Preconditions.notNull(clazz, CLASS_NOT_BE_NULL);
        Preconditions.notNull(name, "Method name must not be null");
        try {
            return clazz.getMethod(name, parameterTypes);
        }
        catch (NoSuchMethodException e) {
            handleReflectionException(e);
        }
        catch (SecurityException e) {
            rethrowRuntimeException(e);
        }
        return null;
    }

    /**
     * Invoke the specified {@link Method} against the supplied target object with no arguments. The target object can
     * be {@code null} when invoking a static {@link Method}.
     * <p>
     * Thrown exceptions are handled via a call to {@link #handleReflectionException}.
     * 
     * @param method the method to invoke
     * @param target the target object to invoke the method on
     * @return the invocation result, if any
     * @see #invokeMethod(Method, Object, Object[])
     */
    public static Object invokeMethod(Method method, Object target) {
        return invokeMethod(method, target, new Object[0]);
    }

    /**
     * Invoke the specified {@link Method} against the supplied target object with the supplied arguments. The target
     * object can be {@code null} when invoking a static {@link Method}.
     * <p>
     * Thrown exceptions are handled via a call to {@link #handleReflectionException}.
     * 
     * @param method the method to invoke
     * @param target the target object to invoke the method on
     * @param args the invocation arguments (may be {@code null})
     * @return the invocation result, if any
     */
    public static Object invokeMethod(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        }
        catch (Exception ex) {
            handleReflectionException(ex);
        }
        throw new IllegalStateException("Should never get here");
    }

    /**
     * Handle the given reflection exception. Should only be called if no checked exception is expected to be thrown by
     * the target method.
     * <p>
     * Throws the underlying RuntimeException or Error in case of an InvocationTargetException with such a root cause.
     * Throws an IllegalStateException with an appropriate message or UndeclaredThrowableException otherwise.
     * 
     * @param ex the reflection exception to handle
     */
    public static void handleReflectionException(Exception ex) {
        if (ex instanceof NoSuchMethodException) {
            throw new IllegalStateException("Method not found: " + ex.getMessage());
        }
        if (ex instanceof IllegalAccessException) {
            throw new IllegalStateException("Could not access method: " + ex.getMessage());
        }
        if (ex instanceof InvocationTargetException) {
            handleInvocationTargetException((InvocationTargetException) ex);
        }
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        }
        throw new UndeclaredThrowableException(ex);
    }

    /**
     * Handle the given invocation target exception. Should only be called if no checked exception is expected to be
     * thrown by the target method.
     * <p>
     * Throws the underlying RuntimeException or Error in case of such a root cause. Throws an
     * UndeclaredThrowableException otherwise.
     * 
     * @param ex the invocation target exception to handle
     */
    public static void handleInvocationTargetException(InvocationTargetException ex) {
        rethrowRuntimeException(ex.getTargetException());
    }

    /**
     * Rethrow the given {@link Throwable exception}, which is presumably the <em>target exception</em> of an
     * {@link InvocationTargetException}. Should only be called if no checked exception is expected to be thrown by the
     * target method.
     * <p>
     * Rethrows the underlying exception cast to a {@link RuntimeException} or {@link Error} if appropriate; otherwise,
     * throws an {@link UndeclaredThrowableException}.
     * 
     * @param ex the exception to rethrow
     * @throws RuntimeException the rethrown exception
     */
    public static void rethrowRuntimeException(Throwable ex) {
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        }
        if (ex instanceof Error) {
            throw (Error) ex;
        }
        throw new UndeclaredThrowableException(ex);
    }

    /**
     * Rethrow the given {@link Throwable exception}, which is presumably the <em>target exception</em> of an
     * {@link InvocationTargetException}. Should only be called if no checked exception is expected to be thrown by the
     * target method.
     * <p>
     * Rethrows the underlying exception cast to an {@link Exception} or {@link Error} if appropriate; otherwise, throws
     * an {@link UndeclaredThrowableException}.
     * 
     * @param ex the exception to rethrow
     * @throws Exception the rethrown exception (in case of a checked exception)
     */
    public static void rethrowException(Throwable ex) throws Exception {
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex instanceof Error) {
            throw (Error) ex;
        }
        throw new UndeclaredThrowableException(ex);
    }

    private static Field[] getDeclaredFields(Class<?> clazz) {
        Preconditions.notNull(clazz, CLASS_NOT_BE_NULL);
        try {
            return clazz.getDeclaredFields();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to introspect Class [" + clazz.getName() + "] from ClassLoader ["
                    + clazz.getClassLoader() + "]", ex);
        }
    }

    public static Method[] getDeclaredMethods(Class<?> clazz) {
        Preconditions.notNull(clazz, CLASS_NOT_BE_NULL);
        Method[] result = null;
        try {
            Method[] declaredMethods = clazz.getDeclaredMethods();
            List<Method> defaultMethods = findConcreteMethodsOnInterfaces(clazz);
            if (defaultMethods != null) {
                result = new Method[declaredMethods.length + defaultMethods.size()];
                System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
                int index = declaredMethods.length;
                for (Method defaultMethod : defaultMethods) {
                    result[index] = defaultMethod;
                    index++;
                }
            }
            else {
                result = declaredMethods;
            }

        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to introspect Class [" + clazz.getName() + "] from ClassLoader ["
                    + clazz.getClassLoader() + "]", ex);
        }

        return result;
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        try {
            Preconditions.notNull(clazz, CLASS_NOT_BE_NULL);
            Preconditions.state(fieldName != null, "Name of the field must be specified");
            Class<?> searchType = clazz;
            while (Object.class != searchType && searchType != null) {
                Field[] fields = getDeclaredFields(searchType);
                for (Field field : fields) {
                    if ((fieldName.equals(field.getName()))) {
                        return field;
                    }
                }
                searchType = searchType.getSuperclass();
            }
            throw new NoSuchFieldException(
                    String.format("Cannot find field %s on class %s", fieldName, clazz.getName()));
        }
        catch (Exception e) {
            rethrowRuntimeException(e);
        }
        return null;
    }

    public static Class<?> resolveFieldType(Class<?> clazz, String fieldName) {
        Field field = findField(clazz, fieldName);
        return field.getType();
    }

    private static List<Method> findConcreteMethodsOnInterfaces(Class<?> clazz) {
        List<Method> result = null;
        for (Class<?> ifc : clazz.getInterfaces()) {
            for (Method ifcMethod : ifc.getMethods()) {
                if (!Modifier.isAbstract(ifcMethod.getModifiers())) {
                    if (result == null) {
                        result = new LinkedList<Method>();
                    }
                    result.add(ifcMethod);
                }
            }
        }
        return result;
    }

    public static Method findGetter(Class<?> clazz, String propertyName) {
        try {
            return findGetter(findProperty(clazz, propertyName));
        }
        catch (UndeclaredThrowableException e) {
            if (e.getCause() instanceof IntrospectionException) {
                return null;
            }
            else {
                throw e;
            }
        }

    }

    public static Method findGetter(PropertyDescriptor propertyDescriptor) {
        return propertyDescriptor.getReadMethod();
    }

    public static Method findSetter(Class<?> clazz, String propertyName) {
        try {
            return findSetter(findProperty(clazz, propertyName));
        }
        catch (UndeclaredThrowableException e) {
            if (e.getCause() instanceof IntrospectionException) {
                return null;
            }
            else {
                throw e;
            }
        }

    }

    public static Method findSetter(PropertyDescriptor propertyDescriptor) {
        return propertyDescriptor.getWriteMethod();
    }

}
