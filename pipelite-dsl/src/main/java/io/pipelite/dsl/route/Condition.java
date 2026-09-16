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
package io.pipelite.dsl.route;

/**
 * Not a third-party extension point. The only place a {@code Condition} is ever evaluated,
 * {@code ExpressionConditionEvaluator} (in {@code pipelite-core}), hard-asserts {@code instanceof
 * ExpressionCondition} and throws {@link IllegalStateException} for anything else — even {@link
 * PayloadTypeCondition} is routed through a completely separate, content-based-routing code path,
 * never through that evaluator. A third-party implementation of this interface would compile fine
 * and fail at runtime the first time it was actually evaluated. {@code sealed} makes that
 * impossibility a compile-time fact instead — mirrors the same fix already applied to {@code
 * DurableInbox} (issue #70) for the same underlying reason: an interface that must stay visible
 * outside its own package but is not actually meant for outside implementation.
 */
public sealed interface Condition permits ExpressionCondition, PayloadTypeCondition {

}
