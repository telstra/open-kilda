/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.config.naming;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

import org.openkilda.config.KafkaTopicsConfig;

import com.google.common.collect.ImmutableMap;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import org.junit.Before;
import org.junit.Test;

public class KafkaNamingForConfigurationValueProcessorTest {
    private static final String TEST_PREFIX = "test_prefix";
    private static final String TEST_VALUE = "test_value";

    private JdkProxyStaticConfigurationFactory factory;

    @Before
    public void setupFactoryWithNamingStrategy() {
        factory = new JdkProxyStaticConfigurationFactory();

        KafkaNamingStrategy namingStrategy = new KafkaNamingStrategy(TEST_PREFIX);
        factory.setConfigurationValueProcessors(
                singletonList(new KafkaNamingForConfigurationValueProcessor(namingStrategy)));
    }

    @Test
    public void shouldApplyNamingToProvidedValue() {
        // given
        MapConfigurationSource source = new MapConfigurationSource(ImmutableMap.of("kafka.topic.ctrl", TEST_VALUE));

        // when
        KafkaTopicsConfig kafkaTopicsConfig = factory.createConfiguration(KafkaTopicsConfig.class, source);

        // then
        assertEquals(TEST_PREFIX + "_" + TEST_VALUE, kafkaTopicsConfig.getCtrlTopic());
    }

    @Test
    public void shouldApplyMappingStrategyToDefaultValue() {
        // given
        MapConfigurationSource source = new MapConfigurationSource(emptyMap());

        // when
        KafkaTopicsConfig kafkaTopicsConfig = factory.createConfiguration(KafkaTopicsConfig.class, source);

        // then
        assertEquals(TEST_PREFIX + "_kilda.ctrl", kafkaTopicsConfig.getCtrlTopic());
    }
}
