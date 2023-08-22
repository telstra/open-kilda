/* Copyright 2023 Telstra Open Source
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

package org.openkilda.floodlight.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Map.Entry;

public final class Utils {
    private Utils() {
    }

    /**
     * Returns simple class name. If simple class name is null of empty - return full class name.
     * NOTE: getSimpleName() cat be empty if it is anonymous class.
     */
    public static String getClassName(Class<?> clazz) {
        if (StringUtils.isBlank(clazz.getSimpleName())) {
            return clazz.getName();
        } else {
            return clazz.getSimpleName();
        }
    }

    /**
     * Converts string: integer map to string.
     */
    public static String mapToString(Map<String, Integer> map) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Entry<String, Integer> entry : map.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append(": ");
            stringBuilder.append(entry.getValue());
        }
        return stringBuilder.toString();
    }
}
