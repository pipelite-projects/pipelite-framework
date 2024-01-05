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
package io.pipelite.expression.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.pipelite.expression.core.el.ast.AbstractLazyFunction;
import io.pipelite.common.support.ToStringBuilder;

public class FunctionRegistryTreeMapImpl extends TreeMap<String, AbstractLazyFunction> implements FunctionRegistry {

    private static final long serialVersionUID = -7384222198162010623L;

    private final Map<String, String> aliases;

    public FunctionRegistryTreeMapImpl() {
        super(String.CASE_INSENSITIVE_ORDER);
        aliases = new HashMap<String, String>();
    }

    @Override
    public AddFunction register(AbstractLazyFunction function) {
        return new AddFunctionImpl(function);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.latitude.governor.expression.impl.FunctionRegistry#tryResolveFunction (java.lang.String)
     */
    @Override
    public Optional<AbstractLazyFunction> tryResolveFunction(String key) {
        return Optional.ofNullable(get(key));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.latitude.governor.expression.impl.OperatorRegistry#containsKey(java. lang.Object)
     */
    @Override
    public boolean containsKey(Object key) {
        if (super.containsKey(key)) {
            return true;
        }
        else {
            return aliases.containsKey(key);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.latitude.governor.expression.impl.OperatorRegistry#get(java.lang. Object)
     */
    @Override
    public AbstractLazyFunction get(Object key) {
        AbstractLazyFunction function = super.get(key);
        if (function == null) {
            if (aliases.containsKey(key)) {
                String functionName = aliases.get(key);
                return super.get(functionName);
            }
            return null;
        }
        else {
            return function;
        }
    }

    public class AddFunctionImpl implements AddFunction {

        private final String functionKey;

        public AddFunctionImpl(AbstractLazyFunction function) {
            this.functionKey = function.getName();
            put(this.functionKey, function);
        }

        public AddFunctionImpl withAlias(String alias) {
            aliases.put(alias, functionKey);
            return this;
        }

    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, false);
        for (Map.Entry<String, AbstractLazyFunction> entry : this.entrySet()) {
            builder.append(entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

}
