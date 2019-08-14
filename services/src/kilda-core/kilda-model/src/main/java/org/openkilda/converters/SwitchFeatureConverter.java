/* Copyright 2019 Telstra Open Source
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

package org.openkilda.converters;

import org.openkilda.model.SwitchFeature;

import org.neo4j.ogm.typeconversion.AttributeConverter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SwitchFeatureConverter implements AttributeConverter<Set<SwitchFeature>, Set<String>> {

    @Override
    public Set<String> toGraphProperty(Set<SwitchFeature> value) {
        if (value == null) {
            return new HashSet<>();
        }
        return value.stream()
                .map(SwitchFeature::toString)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<SwitchFeature> toEntityAttribute(Set<String> value) {
        if (value == null) {
            return new HashSet<>();
        }
        return value.stream()
                .map(String::toUpperCase)
                .map(SwitchFeature::valueOf)
                .collect(Collectors.toSet());
    }
}
