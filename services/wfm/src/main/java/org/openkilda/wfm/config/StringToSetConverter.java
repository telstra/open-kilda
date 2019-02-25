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

package org.openkilda.wfm.config;

import com.sabre.oss.conf4j.converter.TypeConverter;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class StringToSetConverter implements TypeConverter<Set<String>> {

    @Override
    public boolean isApplicable(Type type, Map<String, String> attributes) {
        Objects.requireNonNull(type, "type cannot be null");
        return true;
    }

    @Override
    public Set<String> fromString(Type type, String value, Map<String, String> attributes) {
        if (value != null) {
            String[] regions = value.toLowerCase().trim().split(",");
            if (regions.length == 1) {
                return new HashSet<>(Arrays.asList(regions));
            } else {
                return Arrays.asList(regions).stream().map(String::trim).collect(Collectors.toSet());
            }
        }
        return new HashSet<>();
    }

    @Override
    public String toString(Type type, Set<String> value, Map<String, String> attributes) {
        return String.join(":", value);
    }
}
