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

package org.openkilda.messaging.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * This class is a custom Jackson's JsonInclude validation intended to exclude from JSON
 * an object containing only null fields and non-null but empty Lists.
 */
public final class JsonIncludeObjectHavingNonNullFieldOrNonEmptyList {

    /**
     * This equals method is used in Jackson to determine whether to include or exclude a field.
     * From Jackson's javadoc:<pre>
     *      Filter object's equals() method is called with value to serialize;
     *      if it returns true value is excluded (that is, filtered out); if false value is included.
     * </pre>
     * See Jackson's documentation for more details.
     * @param obj an object to filter
     * @return true for exclude, false for include
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return true;
        }

        try {
            for (Field field : obj.getClass().getDeclaredFields()) {
                if (Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                if (field.getType().isPrimitive()) {
                    return false;
                }

                field.setAccessible(true);
                if (List.class.isAssignableFrom(field.getType())) {
                    if (!((List<?>) field.get(obj)).isEmpty()) {
                        return false;
                    }
                } else {
                    if (field.get(obj) != null) {
                        return false;
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(
                    "The model class is not compatible with this custom JsonInclude implementation. "
                            + "Please either make the model compatible, or implement another JsonInclude", e);
        }

        return true;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not needed, just to suppress check style warnings");
    }
}
