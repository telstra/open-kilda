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

import static java.util.Objects.requireNonNull;

import com.sabre.oss.conf4j.processor.ConfigurationValue;
import com.sabre.oss.conf4j.processor.ConfigurationValueProcessor;

/**
 * Implementation of {@link ConfigurationValueProcessor} responsible for applying mapping to configuration values.
 * It delegates value to {@link MappingStrategy} which confirms that the target is applicable.
 *
 * @see Mapping
 * @see MappingStrategy
 */
public class MappingConfigurationValueProcessor implements ConfigurationValueProcessor {
    private final MappingStrategy[] mappingStrategies;

    public MappingConfigurationValueProcessor(MappingStrategy... mappingStrategies) {
        this.mappingStrategies = mappingStrategies;
    }

    @Override
    public ConfigurationValue process(ConfigurationValue value) {
        requireNonNull(value, "value cannot be null");

        if (value.getAttributes() != null && value.getAttributes().containsKey(Mapping.MAPPING_META_ATTR)) {
            String mappingTarget = value.getAttributes().get(Mapping.MAPPING_META_ATTR);
            for (MappingStrategy mappingStrategy : mappingStrategies) {
                if (mappingStrategy.isApplicable(mappingTarget)) {
                    value.setValue(mappingStrategy.apply(mappingTarget, value.getValue()));
                }
            }
        }

        return value;
    }
}
