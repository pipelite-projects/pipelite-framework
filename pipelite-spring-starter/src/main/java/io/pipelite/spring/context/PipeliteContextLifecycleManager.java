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

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.PipeliteContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

public class PipeliteContextLifecycleManager {

    private final PipeliteContext context;

    public PipeliteContextLifecycleManager(PipeliteContext context) {
        Preconditions.notNull(context, "context is required and cannot be null");
        this.context = context;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void startContext(){
        context.start();
    }

    @EventListener(ContextClosedEvent.class)
    public void stopContext(){
        context.stop();
    }
}
