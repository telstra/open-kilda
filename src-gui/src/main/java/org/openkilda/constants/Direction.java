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

package org.openkilda.constants;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum Direction {
    FORWARD("forward"),
    REVERSE("reverse");

    @JsonValue
    private final String displayName;

    private static final Map<String, Direction> displayNameMap = new HashMap<>();

    static {
        for (Direction direction : Direction.values()) {
            displayNameMap.put(direction.getDisplayName(), direction);
        }
    }

    Direction(String displayName) {
        this.displayName = displayName;
    }

    public static boolean isValidDisplayName(String displayName) {
        return displayNameMap.containsKey(displayName);
    }

    public static Direction byDisplayName(String displayName) {
        return StringUtils.isNotBlank(displayName) ? displayNameMap.get(displayName) : null;
    }
}
