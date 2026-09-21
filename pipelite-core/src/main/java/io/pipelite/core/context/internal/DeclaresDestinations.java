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

import java.util.Collection;

/**
 * Implemented by what sits in a flow and sends exchanges somewhere other than the next step: the
 * router, the recipient list, the wire tap and the exception handlers that dead-letter to a
 * target. It lets the startup validation (issue #88) read those destinations without knowing the
 * concrete classes, most of which are package-private since #82.
 * <p>
 * Only destinations known when the flow is defined: whatever is computed per exchange is not
 * listed.
 */
public interface DeclaresDestinations {

    Collection<DeclaredDestination> declaredDestinations();

}
