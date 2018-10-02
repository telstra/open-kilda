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

import java.util.Arrays;

/**
 * Represents switch statuses.
 */
public enum SwitchStatus {

    /**
     * Switch is in active state.
     */
    ACTIVE("Active"),

    /**
     * Switch is in inactive state.
     */
    INACTIVE("Inactive"),

    /**
     * Switch is removed.
     */
    REMOVED("Removed");

    /**
     * Switch status.
     */
    private final String status;

    /**
     * Instance constructor.
     *
     * @param status switch status.
     */
    SwitchStatus(final String status) {
        this.status = status;
    }

    /**
     * Returns switch status.
     *
     * @return switch status.
     */
    public String getState() {
        return this.status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return status;
    }

    /**
     * Converts a switch status string representation to the instance of {@link SwitchStatus}.
     *
     * @param status the switch state string representation.
     * @return the instance of {@link SwitchStatus}.
     * @throws IllegalArgumentException if the incorrect switch status string representation is passed.
     */
    public static SwitchStatus from(String status) {
        return Arrays.stream(SwitchStatus.values())
                .filter(item -> item.status.equalsIgnoreCase(status))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(String.format("Incorrect switch state: %s", status)));
    }
}
