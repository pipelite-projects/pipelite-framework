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

import io.pipelite.expression.Expression;
import io.pipelite.expression.core.ExpressionImpl;
import io.pipelite.expression.core.el.bean.ElResolver;
import io.pipelite.expression.support.conversion.ConversionService;

public class ExpressionBuilderImpl implements ExpressionBuilder {

    private final ConversionService conversionService;

    private final ElResolver elResolver;

    public ExpressionBuilderImpl(ElResolver elResolver, ConversionService conversionService) {
        this.elResolver = elResolver;
        this.conversionService = conversionService;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.latitude.governor.expression.impl.ExpressionBuilder#build(java.lang.String)
     */
    @Override
    public Expression build(String expression) {
        return new ExpressionImpl(expression, elResolver, conversionService);
    }

}
