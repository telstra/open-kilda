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
 * Represents ISL statuses.
 */
public enum IslStatus {

    /**
     * ISL is in active state.
     */
    ACTIVE("Active"),

    /**
     * ISL is in failed state.
     */
    FAILED("Failed"),

    /**
     * ISL is in moved state.
     */
    MOVED("Moved");

    /**
     * ISL status.
     */
    private final String status;

    /**
     * Instance constructor.
     *
     * @param status ISL status.
     */
    IslStatus(final String status) {
        this.status = status;
    }

    /**
     * Returns ISL status.
     *
     * @return ISL status.
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

    /**
     * Find corresponding value for string representation of status.
     *
     * @param status ISL status.
     * @return {@link IslStatus} value.
     */
    public static IslStatus from(String status) {
        return Arrays.stream(IslStatus.values())
                .filter(item -> item.status.equalsIgnoreCase(status))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Incorrect isl status: %s", status)));
    }
}
