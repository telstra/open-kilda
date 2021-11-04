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

package org.openkilda.rulemanager;

import static java.lang.String.format;

public enum OfVersion {

    OF_12,
    OF_13,
    OF_14,
    OF_15;

    /**
     * Converts String value to enum value.
     */
    public static OfVersion of(String version) {
        switch (version) {
            case "OF_12":
                return OF_12;
            case "OF_13":
                return OF_13;
            case "OF_14":
                return OF_14;
            case "OF_15":
                return OF_15;
            default:
                throw new IllegalStateException(format("Can't parse OpenFlow Version %s", version));
        }
    }
}
