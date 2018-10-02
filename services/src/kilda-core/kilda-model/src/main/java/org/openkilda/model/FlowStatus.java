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
     * Flow creating/deleting state.
     */
    IN_PROGRESS("In progress"),

    /**
     * Flow is in up state.
     */
    UP("Up"),

    /**
     * Flow is in down state.
     */
    DOWN("Down");

    /**
     * Flow status.
     */
    private final String status;

    /**
     * Instance constructor.
     *
     * @param status flow state
     */
    FlowStatus(String status) {
        this.status = status;
    }

    /**
     * Returns flow status.
     *
     * @return flow status.
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return status;
    }

    public boolean isActive() {
        return this == UP;
    }

    public boolean isActiveOrCached() {
        return this == UP;
    }
}

