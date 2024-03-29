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
package io.pipelite.core.flow.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.support.ToStringUtils;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.dsl.route.Condition;
import io.pipelite.dsl.route.ConditionEvaluator;
import io.pipelite.dsl.route.ExpressionCondition;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class RecipientListRouterNode extends AbstractFlowNode implements PipeliteContextAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final RecipientList recipientList;

    private final ConditionEvaluator conditionEvaluator;

    private PipeliteContext pipeliteContext;

    private ExchangeFactory exchangeFactory;

    public RecipientListRouterNode(RecipientList recipientList, ConditionEvaluator conditionEvaluator) {
        Preconditions.notNull(recipientList, "recipientList is required and cannot be null");
        Preconditions.notNull(conditionEvaluator, "conditionEvaluator is required and cannot be null");
        this.recipientList = recipientList;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public void process(Exchange exchange) {

        if(sysLogger.isDebugEnabled()){
            sysLogger.debug("Forwarding exchange to RecipientList {}", ToStringUtils.arrayToString(recipientList.toArray(), 5));
        }

        final Collection<String> filteredRecipients = recipientList.stream()
            .filter(recipient -> {
                if(recipientList.isConditional(recipient)){
                    final Condition condition = recipientList.getCondition(recipient);
                    return conditionEvaluator.evaluate(condition, exchange);
                }
                return true;
            }).collect(Collectors.toList());

        filteredRecipients.forEach(endpointURL -> {
            final Exchange copy = exchangeFactory.copyExchange(exchange);
            pipeliteContext.supplyExchange(endpointURL, copy);
        });
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.pipeliteContext = pipeliteContext;
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
    }
}
