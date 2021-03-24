/* Copyright 2021 Telstra Open Source
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

package org.openkilda.config.converter;

import com.sabre.oss.conf4j.converter.EnumConverter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * This class converts string value to enum and back.
 * Before converting string to enum string case will be changed to upper.
 * After converting enum to string result case will be changed to lower.
 */
public class EnumLowerCaseConverter extends EnumConverter {

    @Override
    public String toString(Type type, Enum<?> value, Map<String, String> attributes) {
        String string = super.toString(type, value, attributes);
        return string == null ? null : string.toLowerCase();
    }

    @Override
    protected Enum<?> toEnumValue(Class<Enum<?>> enumClass, String value) {
        return super.toEnumValue(enumClass, value == null ? null : value.toUpperCase());
    }
}
