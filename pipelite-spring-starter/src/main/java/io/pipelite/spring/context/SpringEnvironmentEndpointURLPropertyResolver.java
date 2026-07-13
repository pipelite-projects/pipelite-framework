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
package io.pipelite.spring.context;

import io.pipelite.core.config.EndpointURLPropertyResolver;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * {@link EndpointURLPropertyResolver} that delegates to {@link Environment#resolveRequiredPlaceholders(String)},
 * inheriting Spring Boot's full placeholder resolution chain (command-line args, system properties, environment
 * variables, {@code application.properties}/{@code .yml}, profiles) with no logic of its own.
 *
 * <p>Fails fast on the first unresolvable key: the {@link IllegalArgumentException} thrown by
 * {@code Environment} is propagated unwrapped, not accumulated across all keys in the URL.
 */
public class SpringEnvironmentEndpointURLPropertyResolver implements EndpointURLPropertyResolver {

    private final Environment environment;

    public SpringEnvironmentEndpointURLPropertyResolver(Environment environment) {
        Assert.notNull(environment, "environment is required and cannot be null");
        this.environment = environment;
    }

    @Override
    public String resolve(String rawUrl) {
        return environment.resolveRequiredPlaceholders(rawUrl);
    }

}
