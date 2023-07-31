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

package org.openkilda.model;

/**
 * Represents flow statuses.
 */
public enum FlowStatus {

    /**
     * Flow is in creating/deleting state.
     */
    IN_PROGRESS,

    /**
     * Flow is in UP state.
     */
    UP,

    /**
     * Flow is in down state.
     */
    DOWN,

    /**
     * Flow is in degraded state.
     */
    DEGRADED;

    /**
     * Converts form path status to corresponding flow status.
     */
    public static FlowStatus convertFromPathStatus(FlowPathStatus pathStatus) {
        switch (pathStatus) {
            case ACTIVE:
                return UP;
            case INACTIVE:
                return DOWN;
            case DEGRADED:
                return DEGRADED;
            case IN_PROGRESS:
                return IN_PROGRESS;
            default:
                throw new IllegalArgumentException(String.format("Unknown path status %s", pathStatus));
        }
    }
}

