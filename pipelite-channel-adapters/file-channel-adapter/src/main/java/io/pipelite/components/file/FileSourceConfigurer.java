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
package io.pipelite.components.file;

import io.pipelite.spi.endpoint.PollingSourceConfigurer;

import java.util.Map;

/**
 * Typed replacement for {@code FileTailPollingConsumer}'s own {@code charset}/{@code
 * startPosition}/{@code recordMapper}/{@code skipLines} query-string parameters, on top of the
 * {@code initialDelay}/{@code period}/{@code timeUnit}/{@code batchSize} every {@link
 * PollingSourceConfigurer} already provides. {@code mode} (today the only source-mode dispatch
 * key, and only ever valid as {@code "tail"} - see {@code FileEndpoint#createConsumer}) is
 * deliberately not exposed here: a single-valued setting has nothing to configure yet, and can be
 * added the moment a second mode actually exists. {@code append} is a producer/sink-only concern
 * ({@code FileProducer}), out of scope for a *source* configurer.
 */
public final class FileSourceConfigurer extends PollingSourceConfigurer {

    private String charset;
    private String startPosition;
    private String recordMapper;
    private Long skipLines;

    public FileSourceConfigurer charset(String charset) {
        this.charset = charset;
        return this;
    }

    public FileSourceConfigurer startPosition(String startPosition) {
        this.startPosition = startPosition;
        return this;
    }

    public FileSourceConfigurer recordMapper(String recordMapperName) {
        this.recordMapper = recordMapperName;
        return this;
    }

    public FileSourceConfigurer skipLines(long skipLines) {
        this.skipLines = skipLines;
        return this;
    }

    @Override
    protected void contributeQueryParameters(Map<String, String> parameters) {
        super.contributeQueryParameters(parameters);
        if (charset != null) {
            parameters.put(FileConstants.CHARSET_PROPERTY_NAME, charset);
        }
        if (startPosition != null) {
            parameters.put(FileConstants.START_POSITION_PROPERTY_NAME, startPosition);
        }
        if (recordMapper != null) {
            parameters.put(FileConstants.RECORD_MAPPER_PROPERTY_NAME, recordMapper);
        }
        if (skipLines != null) {
            parameters.put(FileConstants.SKIP_LINES_PROPERTY_NAME, String.valueOf(skipLines));
        }
    }

}
