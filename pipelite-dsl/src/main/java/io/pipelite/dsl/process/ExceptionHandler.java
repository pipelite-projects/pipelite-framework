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
package io.pipelite.dsl.process;

import io.pipelite.dsl.Exchange;

/**
 * Relocated from {@code io.pipelite.spi.flow.ExceptionHandler} (issue #91) so it can be
 * referenced from the DSL's fluent builder (e.g. {@code BuildOperations#withExceptionHandler}) —
 * {@code pipelite-dsl} cannot depend on {@code pipelite-spi} (module dependency direction is
 * {@code pipelite-common} &lt;- {@code pipelite-dsl} &lt;- {@code pipelite-spi} &lt;-
 * {@code pipelite-core}, never the reverse). Keyed to {@link Exchange} instead of the SPI-level
 * {@code ExchangeImpl} - the same bridge {@link Processor#process(Exchange, ProcessContribution)}
 * already relies on, since {@code ExchangeImpl implements Exchange}. One interface, reused
 * everywhere: every SPI-level call site already invokes {@code handleException(exception,
 * exchange)} with a real {@code ExchangeImpl}, which upcasts to {@code Exchange} with no
 * conversion.
 */
public interface ExceptionHandler {

    void handleException(Throwable exception, Exchange exchange);

}
