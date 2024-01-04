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

import io.pipelite.core.flow.execution.FlowExecutionDump;

import java.time.LocalDateTime;

public class SerializedFlowExecutionDump extends AbstractFlowExecutionDump implements FlowExecutionDump {

    private String exchangeData;
    private String encoding;

    public static SerializedFlowExecutionDump createNow(String id, String flowHash, String flowName){
        return new SerializedFlowExecutionDump(id, flowHash, flowName, LocalDateTime.now());
    }

    public SerializedFlowExecutionDump(String id, String flowHash, String flowName, LocalDateTime creationTime) {
        super(id, flowHash, flowName, creationTime);
    }

    public String getExchangeData() {
        return exchangeData;
    }

    public String getEncoding(){
        return encoding;
    }

    public void setExchangeData(String exchangeData, String encoding) {
        this.exchangeData = exchangeData;
        this.encoding = encoding;
    }
}
