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
package io.pipelite.dsl.definition;

import java.util.function.Consumer;

public interface SinkDefinition extends EndpointDefinition {

    /**
     * The lambda passed to {@code toSink(url, configurer)}, erased to the common {@link SinkConfigurer} type - {@code
     * null} if this sink was declared via the plain {@code toSink(String)} overload. Applied by {@code
     * DefaultEndpointFactory} once the concrete configurer for this sink's actual adapter is known, the way the callback
     * of a source is ({@link SourceDefinition#getConfigurerCallback()}).
     */
    Consumer<SinkConfigurer> getConfigurerCallback();
}
