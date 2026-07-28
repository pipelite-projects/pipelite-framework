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
package io.pipelite.core.definition.builder.split;

import io.pipelite.core.definition.builder.Builder;
import io.pipelite.dsl.process.Processor;
import io.pipelite.dsl.split.SplitSegment;
import io.pipelite.dsl.split.SplitSegmentImpl;
import io.pipelite.dsl.split.SplitSegmentOperations;
import io.pipelite.dsl.split.SplitStep;

public class SplitSegmentBuilder implements SplitSegmentOperations {

    private final Builder<SplitSegmentImpl> builder = Builder.forType(SplitSegmentImpl.class);

    @Override
    public SplitSegmentOperations process(String name, Processor processor) {
        final SplitStep step = new SplitStep(name, processor);
        builder.with(target -> target.add(step));
        return this;
    }

    @Override
    public SplitSegment end() {
        return builder.build();
    }
}
