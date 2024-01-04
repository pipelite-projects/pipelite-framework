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
package io.pipelite.channels.kafka;

import io.pipelite.channels.kafka.config.DefaultKafkaChannelConfiguration;
import io.pipelite.channels.kafka.config.KafkaChannelConfigurer;
import org.junit.Assert;
import org.junit.Test;

public class KafkaChannelConfigurerTest {

    @Test
    public void shouldBeStraightForward(){

        final KafkaChannelConfigurer configurer = (configuration) -> {
            configuration.setBootstrapServers("localhost:9093");
        };

        final DefaultKafkaChannelConfiguration configuration = new DefaultKafkaChannelConfiguration();
        configurer.configure(configuration);

        Assert.assertNotNull(configurer);
        Assert.assertNotNull(configuration);
        Assert.assertEquals("localhost:9093", configuration.getBootstrapServers());

    }

    @Test
    public void givenNoConfiguration_whenReadConfiguration_thenGetDefaultValues(){

        final KafkaChannelConfigurer configurer = (configuration) -> {};

        final DefaultKafkaChannelConfiguration configuration = new DefaultKafkaChannelConfiguration();
        configurer.configure(configuration);

        Assert.assertNotNull(configurer);
        Assert.assertNotNull(configuration);
        Assert.assertEquals("localhost:9092", configuration.getBootstrapServers());

    }

}
