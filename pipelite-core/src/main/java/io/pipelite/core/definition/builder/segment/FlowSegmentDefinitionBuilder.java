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
package io.pipelite.core.definition.builder.segment;

import io.pipelite.core.definition.builder.Builder;
import io.pipelite.core.flow.process.filter.ExpressionFilterNode;
import io.pipelite.core.flow.process.transform.PayloadTransformerNode;
import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.Processor;
import io.pipelite.dsl.segment.FlowSegmentDefinition;
import io.pipelite.dsl.segment.FlowSegmentDefinitionImpl;
import io.pipelite.dsl.segment.FlowSegmentOperations;
import io.pipelite.dsl.segment.SegmentStep;
import io.pipelite.dsl.segment.SegmentWireTapStep;
import io.pipelite.expression.ExpressionParser;

public class FlowSegmentDefinitionBuilder implements FlowSegmentOperations {

    private final Builder<FlowSegmentDefinitionImpl> builder = Builder.forType(FlowSegmentDefinitionImpl.class);
    private final ExpressionParser expressionParser = new ExpressionParser();

    @Override
    public FlowSegmentOperations process(String name, Processor processor) {
        final SegmentStep step = new SegmentStep(name, processor);
        builder.with(target -> target.add(step));
        return this;
    }

    @Override
    public FlowSegmentOperations filter(String name, String expression) {
        final Processor processor = new ExpressionFilterNode(expression, expressionParser);
        final SegmentStep step = new SegmentStep(name, processor);
        builder.with(target -> target.add(step));
        return this;
    }

    @Override
    public FlowSegmentOperations transformPayload(String name, PayloadTransformer payloadTransformer) {
        final Processor processor = new PayloadTransformerNode(payloadTransformer);
        final SegmentStep step = new SegmentStep(name, processor);
        builder.with(target -> target.add(step));
        return this;
    }

    @Override
    public FlowSegmentOperations wireTap(String name, String endpointURL) {
        final SegmentWireTapStep step = new SegmentWireTapStep(name, endpointURL);
        builder.with(target -> target.add(step));
        return this;
    }

    @Override
    public FlowSegmentDefinition end() {
        return builder.build();
    }
}
