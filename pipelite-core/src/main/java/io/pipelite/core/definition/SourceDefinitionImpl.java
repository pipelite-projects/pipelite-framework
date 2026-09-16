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
package io.pipelite.core.definition;

import io.pipelite.dsl.definition.SourceConfigurer;
import io.pipelite.dsl.definition.SourceDefinition;

import java.util.function.Consumer;

public class SourceDefinitionImpl extends EndpointDefinitionImpl implements SourceDefinition {

    private final Consumer<SourceConfigurer> configurerCallback;

    public SourceDefinitionImpl(String url) {
        this(url, null);
    }

    public SourceDefinitionImpl(String url, Consumer<SourceConfigurer> configurerCallback) {
        super(url);
        this.configurerCallback = configurerCallback;
    }

    @Override
    public Consumer<SourceConfigurer> getConfigurerCallback() {
        return configurerCallback;
    }

}
