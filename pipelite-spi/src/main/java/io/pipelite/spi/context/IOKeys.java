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
package io.pipelite.spi.context;

public class IOKeys {

    public static final String RETURN_ADDRESS_HEADER_NAME = "X-Return-Address";
    public static final String ROUTING_SLIP_PROPERTY_NAME = "X-Routing-Slip";

    public static final String FAILURE_EXCEPTION_TYPE_HEADER_NAME = "X-Failure-Exception-Type";
    public static final String FAILURE_EXCEPTION_MESSAGE_HEADER_NAME = "X-Failure-Exception-Message";

    public static final String FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME = "X-FlowExecution-Dump-Id";
    public static final String FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME = "X-FlowExecution-Last-Executed-Flow";
    public static final String FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME = "X-FlowExecution-Last-Executed-Flow-Source-Endpoint-Resource";
    public static final String FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME = "X-FlowExecution-Last-Executed-Processor";
    public static final String FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME = "X-FlowExecution-Attempt-Number";

    private IOKeys(){
    }
}
