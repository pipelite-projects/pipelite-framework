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
package io.pipelite.core.context.internal;

/**
 * A destination a flow was declared to send exchanges to, and the DSL construct it was written in.
 * Public only because the nodes and handlers that declare it live in other packages of {@code
 * pipelite-core}: not part of the API.
 *
 * @param url        the destination as written in the DSL, not resolved and possibly an expression
 * @param declaredBy the DSL construct, e.g. {@code toRoute(...)}
 */
public record DeclaredDestination(String url, String declaredBy) {
}
