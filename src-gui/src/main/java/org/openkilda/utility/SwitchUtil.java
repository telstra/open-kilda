/* Copyright 2024 Telstra Open Source
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

package org.openkilda.utility;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public final class SwitchUtil {

    private SwitchUtil() {
    }

    /**
     * Custom switch name.
     *
     * @param csNames the cs names
     * @param switchId the switch id
     * @return the string
     */
    public static String customSwitchName(final Map<String, String> csNames, final String switchId) {
        if (MapUtils.isEmpty(csNames)) {
            return switchId;
        }
        if (csNames.containsKey(switchId.toLowerCase()) || csNames.containsKey(switchId.toUpperCase())) {
            return StringUtils.isBlank(csNames.get(switchId)) ? switchId : csNames.get(switchId);
        } else {
            return switchId;
        }
    }
}
