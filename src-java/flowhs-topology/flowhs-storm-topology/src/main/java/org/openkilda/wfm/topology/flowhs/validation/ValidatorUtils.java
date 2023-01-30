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

package org.openkilda.wfm.topology.flowhs.validation;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;

import java.util.concurrent.TimeUnit;

public final class ValidatorUtils {

    private ValidatorUtils() {}

    /**
     * maxLatencyValidator method checks if the maxLatency and maxLatencyTier2 are valid.
     * @param maxLatency maxLatency property
     * @param maxLatencyTier2 maxLatencyTier2 property
     * @throws InvalidFlowException invalidFlow exception
     */
    public static void validateMaxLatencyAndLatencyTier(Long maxLatency, Long maxLatencyTier2) throws InvalidFlowException {
        if (maxLatency == null && maxLatencyTier2 == null) {
            return;
        }
        if (maxLatency == null) {
            throw new InvalidFlowException(
                    "maxLatencyTier2 property cannot be used without maxLatency",
                    ErrorType.DATA_INVALID);
        }
        if (maxLatencyTier2 == null || maxLatencyTier2.equals(0L)) {
            return;
        }

        if (maxLatency < 0) {
            throw new InvalidFlowException("maxLatency cannot be negative", ErrorType.DATA_INVALID);
        }
        if (maxLatencyTier2 < 0) {
            throw new InvalidFlowException("maxLatencyTier2 cannot be negative", ErrorType.DATA_INVALID);
        }
        if (maxLatency.equals(0L) && maxLatencyTier2.equals(0L)) {
            return;
        }
        if (maxLatency > maxLatencyTier2) {
            throw new InvalidFlowException(
                    format("The maxLatency %dms is higher than maxLatencyTier2 %dms",
                            TimeUnit.NANOSECONDS.toMillis(maxLatency),
                            TimeUnit.NANOSECONDS.toMillis(maxLatencyTier2)),
                    ErrorType.DATA_INVALID);
        }
    }
}
