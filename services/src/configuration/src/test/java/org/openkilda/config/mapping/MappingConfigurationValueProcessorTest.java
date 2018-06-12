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

package org.openkilda.config.mapping;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import org.junit.Before;
import org.junit.Test;

public class MappingConfigurationValueProcessorTest {
    private static final String TEST_VALUE = "test_value";
    private static final String TEST_DEFAULT_VALUE = "test_default";
    private static final String TEST_MAPPING_TARGET = "test_target";

    private JdkProxyStaticConfigurationFactory factory;
    private MappingStrategy mappingStrategy;

    @Before
    public void setupFactoryWithMappingStrategy() {
        factory = new JdkProxyStaticConfigurationFactory();

        // Define mapping for TEST_MAPPING_TARGET.
        mappingStrategy = mock(MappingStrategy.class);
        when(mappingStrategy.isApplicable(eq(TEST_MAPPING_TARGET))).thenReturn(true);
        when(mappingStrategy.apply(eq(TEST_MAPPING_TARGET), any())).thenReturn("mapped_value");
        factory.setConfigurationValueProcessors(singletonList(new MappingConfigurationValueProcessor(mappingStrategy)));
    }

    @Test
    public void shouldPerformMappingForMatchedTarget() {
        // given
        MapConfigurationSource source = new MapConfigurationSource(ImmutableMap.of("test_key", TEST_VALUE));

        // when
        factory.createConfiguration(TestConfig.class, source);

        // then
        verify(mappingStrategy).apply(eq(TEST_MAPPING_TARGET), eq(TEST_VALUE));
    }

    @Test
    public void shouldPerformMappingForDefaultValue() {
        // given
        MapConfigurationSource source = new MapConfigurationSource(emptyMap());

        // when
        factory.createConfiguration(TestConfig.class, source);

        // then
        verify(mappingStrategy).apply(eq(TEST_MAPPING_TARGET), eq(TEST_DEFAULT_VALUE));
    }

    @Configuration
    public interface TestConfig {
        @Key("test_key")
        @Default(TEST_DEFAULT_VALUE)
        @Mapping(target = TEST_MAPPING_TARGET)
        String getTestProperty();
    }
}
