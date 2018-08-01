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

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.sabre.oss.conf4j.factory.ConfigurationFactory;
import com.sabre.oss.conf4j.source.ConfigurationSource;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

/**
 * This class creates a configuration instance, fills it with values from the source
 * and validates against the constraints using validation-api.
 * <p/>
 *
 * @see ConfigurationSource
 * @see ConfigurationFactory
 * @see Validator
 */
public class ValidatingConfigurationProvider {
    protected final ConfigurationSource source;
    protected final ConfigurationFactory factory;
    private final Validator validator;

    public ValidatingConfigurationProvider(ConfigurationSource source, ConfigurationFactory factory) {
        this.source = source;
        this.factory = factory;

        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * Creates, fills and validates a configuration instance for specified type.
     *
     * @param configurationType configuration class.
     * @return configuration instance
     */
    public <T> T getConfiguration(Class<T> configurationType) {
        requireNonNull(configurationType, "configurationType cannot be null");

        T instance = factory.createConfiguration(configurationType, source);

        Set<ConstraintViolation<T>> errors = validator.validate(instance);
        if (!errors.isEmpty()) {
            Set<String> errorDetails = errors.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(toSet());

            throw new ConfigurationException(
                    format("The configuration value(s) for %s violate constraint(s): %s",
                            configurationType.getSimpleName(), String.join(";", errorDetails)), errorDetails);
        }

        return instance;
    }
}
