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

package org.openkilda.wfm.config.provider;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Key;
import org.junit.Test;

import java.util.Properties;

public class MultiPrefixConfigurationProviderTest {
    static final String TEST_PREFIX = "prefix";
    static final String TEST_KEY = "test_key";
    static final String TEST_VALUE = "test_value";

    @Test
    public void shouldNotLoadConfigFromPropertiesWithoutPrefixes() {

        // given
        Properties source = new Properties();
        source.setProperty(format("%s.%s", TEST_PREFIX, TEST_KEY), TEST_VALUE);
        MultiPrefixConfigurationProvider provider = new MultiPrefixConfigurationProvider(source);

        // when
        TestConfig config = provider.getConfiguration(TestConfig.class);

        // then
        assertNull(config.getTestProperty());
    }

    @Test
    public void shouldLoadConfigFromPropertiesWithPrefixes() {
        // given
        Properties source = new Properties();
        source.setProperty(format("%s.%s", TEST_PREFIX, TEST_KEY), TEST_VALUE);
        MultiPrefixConfigurationProvider provider = new MultiPrefixConfigurationProvider(source, TEST_PREFIX);

        // when
        TestConfig config = provider.getConfiguration(TestConfig.class);

        // then
        assertEquals(TEST_VALUE, config.getTestProperty());
    }

    @Configuration
    public interface TestConfig {
        @Key(TEST_KEY)
        String getTestProperty();
    }
}
