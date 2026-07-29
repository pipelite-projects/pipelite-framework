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
package io.pipelite.dsl.segment;

/**
 * Marker for the two kinds of entries a {@link FlowSegmentDefinition} can hold: a plain
 * {@link SegmentStep} (backed by a {@code Processor} - covers {@code process}/{@code filter}/
 * {@code transformPayload}) or a {@link SegmentWireTapStep}. Kept as two distinct types, rather
 * than forcing wireTap through the same {@code (name, Processor)} shape, because a wireTap needs
 * {@code PipeliteContext}/{@code ExchangeFactory} to dispatch a copy to another endpoint - access
 * a plain {@code Processor} does not have.
 */
public interface SegmentStepDefinition {

    String getName();
}
