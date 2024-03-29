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
package io.pipelite.core.flow.execution.dump;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.flow.execution.FlowExecutionDump;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractFlowExecutionDump implements FlowExecutionDump {

    private final String id;
    private final LocalDateTime creationTime;
    private final String flowHash;
    private final String flowName;
    private String sourceEndpointResource;
    private String lastExecutedProcessor;
    private int attemptNumber;

    private transient Throwable failureException;

    public AbstractFlowExecutionDump(String id, String flowHash, String flowName, LocalDateTime creationTime) {
        Preconditions.notNull(id, "id is required and cannot be null");
        Preconditions.notNull(flowHash, "flowHash is required and cannot be null");
        Preconditions.notNull(flowName, "flowName is required and cannot be null");
        Preconditions.notNull(creationTime, "creationTime is required and cannot be null");
        this.id = id;
        this.flowHash = flowHash;
        this.flowName = flowName;
        this.creationTime = creationTime;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    @Override
    public String getFlowHash() {
        return flowHash;
    }

    @Override
    public String getFlowName() {
        return flowName;
    }

    @Override
    public String getSourceEndpointResource() {
        return sourceEndpointResource;
    }

    public void setSourceEndpointResource(String sourceEndpointResource) {
        this.sourceEndpointResource = sourceEndpointResource;
    }

    @Override
    public void setLastExecutedProcessor(String processorName){
        lastExecutedProcessor = processorName;
    }

    @Override
    public String getLastExecutedProcessor() {
        return lastExecutedProcessor;
    }

    @Override
    public void setAttemptNumber(int attemptNumber){
        this.attemptNumber = attemptNumber;
    }

    @Override
    public int getAttemptNumber() {
        return attemptNumber;
    }

    @Override
    public void setFailureException(Throwable failureException) {
        this.failureException = failureException;
    }

    @Override
    public Optional<Throwable> tryGetFailureException() {
        return Optional.ofNullable(failureException);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractFlowExecutionDump that = (AbstractFlowExecutionDump) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
