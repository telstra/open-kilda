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

package org.openkilda.config.provider;

import static org.junit.Assert.assertEquals;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Key;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.PropertiesConfigurationSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Properties;
import javax.validation.constraints.Min;

public class ValidatingConfigurationProviderTest {
    static final String TEST_KEY = "test_key";
    static final int VALID_TEST_VALUE = 100;
    static final int INVALID_TEST_VALUE = -1;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldPassValidationForValidConfig() {
        // given
        Properties source = new Properties();
        source.setProperty(TEST_KEY, String.valueOf(VALID_TEST_VALUE));

        ValidatingConfigurationProvider provider = new ValidatingConfigurationProvider(
                new PropertiesConfigurationSource(source), new JdkProxyStaticConfigurationFactory());

        // when
        TestConfig config = provider.getConfiguration(TestConfig.class);

        // then
        assertEquals(VALID_TEST_VALUE, config.getTestProperty());
    }

    @Test
    public void shouldFailValidationForInvalidConfig() {
        // given
        Properties source = new Properties();
        source.setProperty(TEST_KEY, String.valueOf(INVALID_TEST_VALUE));

        ValidatingConfigurationProvider provider = new ValidatingConfigurationProvider(
                new PropertiesConfigurationSource(source), new JdkProxyStaticConfigurationFactory());

        // when
        expectedException.expect(ConfigurationException.class);
        provider.getConfiguration(TestConfig.class);

        // then ConfigurationException is thrown
    }

    @Configuration
    public interface TestConfig {
        @Key(TEST_KEY)
        @Min(1)
        int getTestProperty();
    }
}
