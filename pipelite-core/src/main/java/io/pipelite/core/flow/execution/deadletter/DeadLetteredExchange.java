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
package io.pipelite.core.flow.execution.deadletter;

import java.time.LocalDateTime;

/**
 * A single entry in the framework's built-in dead letter queue (issue #93) - mirrors {@code
 * SerializedFlowExecutionDump}, but write-only: no claim state, no repository read/poll API yet
 * (a future UI reads these files directly, the same way a retry dump is human-readable
 * {@code Properties} text today).
 */
public class DeadLetteredExchange {

    private final String id;
    private final LocalDateTime creationTime;
    private final String flowName;
    private String sourceEndpointResource;
    private String failedProcessor;
    private String exceptionType;
    private String exceptionMessage;
    private String stackTrace;
    private String exchangeData;
    private String encoding;

    public static DeadLetteredExchange createNow(String id, String flowName) {
        return new DeadLetteredExchange(id, flowName, LocalDateTime.now());
    }

    public DeadLetteredExchange(String id, String flowName, LocalDateTime creationTime) {
        this.id = id;
        this.flowName = flowName;
        this.creationTime = creationTime;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    public String getFlowName() {
        return flowName;
    }

    public String getSourceEndpointResource() {
        return sourceEndpointResource;
    }

    public void setSourceEndpointResource(String sourceEndpointResource) {
        this.sourceEndpointResource = sourceEndpointResource;
    }

    public String getFailedProcessor() {
        return failedProcessor;
    }

    public void setFailedProcessor(String failedProcessor) {
        this.failedProcessor = failedProcessor;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getExchangeData() {
        return exchangeData;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setExchangeData(String exchangeData, String encoding) {
        this.exchangeData = exchangeData;
        this.encoding = encoding;
    }

}
