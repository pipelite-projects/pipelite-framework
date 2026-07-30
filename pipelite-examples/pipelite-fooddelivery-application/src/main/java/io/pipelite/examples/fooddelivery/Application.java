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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipelite.spring.EnablePipelite;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Food-delivery order processing demo: a Pipelite application built from several
 * {@code link://}-chained flows, two of them independently concurrent, fed by four different
 * channel adapters (HTTP, File, Kafka, Time). See {@link FoodDeliveryFlowConfiguration} for the
 * flow topology and {@link FoodDeliveryChannelConfiguration} for infrastructure wiring (shared
 * pool size, Kafka/File adapter configuration) — both are ordinary Spring beans, constructed and
 * wired by this {@code @SpringBootApplication} exactly like any other Spring Boot app.
 * {@code context.start()}/{@code context.stop()} are not called manually anywhere: {@code
 * pipelite-spring-starter}'s {@code PipeliteContextLifecycleManager} ties them to Spring's own
 * {@code ContextRefreshedEvent}/{@code ContextClosedEvent}, so Ctrl+C triggers a clean shutdown
 * of every flow via Spring Boot's own shutdown hook.
 *
 * <h2>How to run</h2>
 * <ol>
 *   <li>Start a local Kafka broker: {@code docker compose up -d} from this module's directory
 *       (see {@code docker-compose.yml}, a single-node KRaft broker on {@code localhost:9092}).
 *       Without it, the kitchen-processing → Kafka → dispatch-processing hop simply won't
 *       deliver anything, but the HTTP/File ingress into the kitchen stage still works.</li>
 *   <li>Run this class ({@code mvn spring-boot:run} from this module, or your IDE). The HTTP
 *       ingress binds to port 80 (fixed by the http-channel-adapter) — free it first if
 *       something else is already listening there.</li>
 *   <li>Watch the console: {@code orderGeneratorFlow}'s {@code time://} tick drives
 *       {@link OrderGenerator} to emit ~40 simulated orders over HTTP and the partner CSV file,
 *       so kitchen/dispatch worker overlap is visible immediately with no manual input.</li>
 *   <li>Inspect {@code %TEMP%/pipelite-fooddelivery-demo/delivery-log-<driver>.csv} (see
 *       {@link FoodDeliveryPaths}) — one audit-trail file per courier, written by the dispatch
 *       stage's routing table.</li>
 *   <li>Ctrl+C to stop; every flow shuts down cleanly via Spring's own lifecycle.</li>
 * </ol>
 *
 * <p>Kafka's bootstrap servers can be overridden via the {@code kafka.bootstrap-servers}
 * property (or the equivalent {@code KAFKA_BOOTSTRAP_SERVERS} environment variable, per Spring
 * Boot's relaxed binding) — see {@link FoodDeliveryChannelConfiguration}. Defaults to {@code
 * localhost:9092}, matching the bundled compose file.
 */
@SpringBootApplication
@EnablePipelite(flowConfigurations = FoodDeliveryFlowConfiguration.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Explicit bean rather than relying on Spring Boot's {@code JacksonAutoConfiguration}: this
     * module only pulls in {@code jackson-databind} transitively (via {@code
     * kafka-channel-adapter}), not the full {@code spring-boot-starter-json} set Boot's
     * auto-configuration expects, so it does not fire here.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
