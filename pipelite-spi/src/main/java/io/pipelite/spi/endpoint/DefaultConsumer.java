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
package io.pipelite.spi.endpoint;

import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultConsumer extends AbstractConsumer implements Consumer {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    public DefaultConsumer(Endpoint endpoint){
        super(endpoint);
    }

    @Override
    public void consume(Exchange exchange) {
        try{
            process(exchange);
        }catch(Throwable exception){
            if(exceptionHandler != null){
                exceptionHandler.handleException(exception, exchange);
            }else if(sysLogger.isErrorEnabled()){
                sysLogger.error("An underlying error occurred in DefaultConsumer", exception);
            }
        }
    }

    @Override
    public void process(Exchange exchange) {
        if(!hasNext()){
            throw new IllegalStateException("DefaultConsumer must have a next FlowNode");
        }
        next.process(exchange);
    }

}
