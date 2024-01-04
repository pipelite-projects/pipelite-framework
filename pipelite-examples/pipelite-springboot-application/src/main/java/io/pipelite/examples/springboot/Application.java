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
package io.pipelite.examples.springboot;

import io.pipelite.core.Pipelite;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spring.EnablePipelite;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class Application {

    public static void main(String[] args){
        SpringApplication.run(ApplicationConfiguration.class);
    }

    @Configuration
    @EnablePipelite
    public static class ApplicationConfiguration {

        @Bean
        public FlowDefinition acquireFlow(){
            return Pipelite.defineFlow("acquire-flow")
                .fromSource("http://ingress")
                .wireTap("wiretap-logger", "slf4j://wire-tap-logger")
                .toSink("slf4j://main-logger")
                .build();
        }

    }

}
