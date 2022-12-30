/* Copyright 2022 Telstra Open Source
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

package org.openkilda.pce.finder;

import static java.lang.String.format;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public final class FinderUtils {

    public static final String REASONS_KEYWORD = "Reasons";

    private FinderUtils() {}

    /**
     * Returns a formatted string.
     * @param reasons map of fail reasons
     * @return formatted string
     */
    public static String reasonsToString(Map<FailReasonType, FailReason> reasons) {
        if (reasons != null && !reasons.isEmpty()) {
            return format("%s: %s", REASONS_KEYWORD, StringUtils.join(reasons.values(), ", "));
        }
        return "";
    }
}
