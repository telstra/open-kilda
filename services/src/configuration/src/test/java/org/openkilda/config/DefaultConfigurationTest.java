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

package org.openkilda.config;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Key;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import org.junit.Test;

public class DefaultConfigurationTest {
    private JdkProxyStaticConfigurationFactory factory = new JdkProxyStaticConfigurationFactory();

    @Test
    public void shouldSetDefaultValueIfNotProvided() {
        // given
        MapConfigurationSource source = new MapConfigurationSource(emptyMap());

        // when
        KafkaTopicsConfig kafkaTopicsConfig = factory.createConfiguration(KafkaTopicsConfig.class, source);

        // then
        assertEquals("kilda.ctrl", kafkaTopicsConfig.getCtrlTopic());
    }

    @Test
    public void shouldBeNullIfKeyAbsent() {
        // given
        MapConfigurationSource source = new MapConfigurationSource(emptyMap());

        // when
        TestConfig testConfig = factory.createConfiguration(TestConfig.class, source);

        // then
        assertNull(testConfig.getTestProperty());
    }

    @Configuration
    public interface TestConfig {
        @Key("test_key")
        Integer getTestProperty();
    }
}
