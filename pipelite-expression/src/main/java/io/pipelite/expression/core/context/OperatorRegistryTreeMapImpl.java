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

import io.pipelite.expression.core.el.ast.Operator;
import io.pipelite.expression.core.el.ast.UnaryOperator;
import io.pipelite.common.support.ToStringBuilder;

public class OperatorRegistryTreeMapImpl extends TreeMap<String, Operator> implements OperatorRegistry {

    private static final long serialVersionUID = -7384222198162010623L;

    private Map<String, String> aliases;

    public OperatorRegistryTreeMapImpl() {
        super(String.CASE_INSENSITIVE_ORDER);
        aliases = new HashMap<String, String>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.latitude.governor.expression.impl.OperatorRegistry#register(com.
     * latitude.governor.expression.core.evaluation.Operator)
     */
    @Override
    public AddOperation register(Operator operator) {
        return new AddOperationImpl(operator);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.latitude.governor.expression.impl.OperatorRegistry#tryResolveOperator (java.lang.String)
     */
    @Override
    public Optional<Operator> tryResolveOperator(String key) {
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
    public Operator get(Object key) {
        Operator operator = super.get(key);
        if (operator == null) {
            if (aliases.containsKey(key)) {
                String operatorName = aliases.get(key);
                return super.get(operatorName);
            }
            return null;
        }
        else {
            return operator;
        }
    }

    public class AddOperationImpl implements AddOperation {

        private final String operatorKey;

        public AddOperationImpl(Operator operator) {
            String operatorKey = operator.getName();
            if (operator instanceof UnaryOperator) {
                operatorKey = String.format("u%s", operator.getName());
            }
            this.operatorKey = operatorKey;
            put(this.operatorKey, operator);
        }

        public AddOperationImpl withAlias(String alias) {
            aliases.put(alias, operatorKey);
            return this;
        }

    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, false);
        for (Map.Entry<String, Operator> entry : this.entrySet()) {
            builder.append(entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

}
