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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.IgnoreKey;
import com.sabre.oss.conf4j.annotation.Key;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import org.junit.Test;

public class CompositeConfigurationTest {
    private JdkProxyStaticConfigurationFactory factory = new JdkProxyStaticConfigurationFactory();

    @Test
    public void shouldProcessCompositeConfigs() {
        // given
        MapConfigurationSource source = new MapConfigurationSource(ImmutableMap.of("test_key", "10"));

        // when
        TestCompositeConfig testCompositeConfig = factory.createConfiguration(TestCompositeConfig.class, source);

        // then
        assertEquals(10, testCompositeConfig.getTestProperty());
    }

    @Configuration
    public interface TestCompositeConfig {
        @IgnoreKey
        TestComponentConfig getTestComponent();

        default int getTestProperty() {
            return getTestComponent().getTestProperty();
        }
    }

    @Configuration
    public interface TestComponentConfig {
        @Key("test_key")
        int getTestProperty();
    }
}
