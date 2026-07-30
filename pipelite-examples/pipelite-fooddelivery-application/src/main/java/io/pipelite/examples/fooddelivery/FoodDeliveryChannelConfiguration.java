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
package io.pipelite.examples.fooddelivery;

import io.pipelite.channels.kafka.config.KafkaChannelConfigurer;
import io.pipelite.components.file.FileChannelConfigurer;
import io.pipelite.core.context.ConfigurablePipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.spring.context.PipeliteContextInitializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Any Spring bean implementing {@link PipeliteContextInitializer} is called with the
 * {@link PipeliteContext} before {@code context.start()} runs (see {@code
 * PipeliteContextInitializerBeanPostProcessor} in {@code pipelite-spring-starter}) — the
 * Spring-idiomatic place to configure infrastructure that in the plain-Java sibling example
 * would sit inline in {@code main()}: the shared source-worker-pool budget and the two channel
 * adapters' settings.
 */
@Component
public class FoodDeliveryChannelConfiguration implements PipeliteContextInitializer {

    private final String kafkaBootstrapServers;

    public FoodDeliveryChannelConfiguration(
            @Value("${kafka.bootstrap-servers:localhost:9092}") String kafkaBootstrapServers) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    @Override
    public void initializeContext(PipeliteContext context) {

        final ConfigurablePipeliteContext configurableContext = (ConfigurablePipeliteContext) context;

        // The kitchen (concurrency=4) and dispatch (concurrency=3) stages share this one budget
        // rather than each getting a dedicated pool — 20 gives comfortable headroom over the
        // 7 declared concurrent slots while still demonstrating a framework-owned, bounded cap.
        configurableContext.setMaxSourceWorkerPoolSize(20);

        configurableContext.addChannelConfigurer(
            (KafkaChannelConfigurer) configuration -> configuration.setBootstrapServers(kafkaBootstrapServers));
        configurableContext.addChannelConfigurer(
            (FileChannelConfigurer) configuration -> configuration.setStateDirectory(FoodDeliveryPaths.FILE_ADAPTER_STATE_DIRECTORY));
    }

}
