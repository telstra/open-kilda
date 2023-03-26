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

package org.openkilda.wfm.topology.utils;

public class SequentialNumberGenerator {
    private final long lowerLimit;
    private final long upperLimit;
    private long value;

    public SequentialNumberGenerator() {
        this(0, Long.MAX_VALUE);
    }

    public SequentialNumberGenerator(long lowerLimit, long upperLimit) {
        if (upperLimit <= lowerLimit) {
            throw new IllegalArgumentException(String.format(
                    "upper limit is less or equal lower limit (%d <= %d)", upperLimit, lowerLimit));
        }

        this.lowerLimit = value = lowerLimit;
        this.upperLimit = upperLimit;
    }

    /**
     * Generate next long value.
     */
    public long generateLong() {
        long result = value;
        if (upperLimit <= ++value) {
            value = lowerLimit;
        }
        return result;
    }

    public int generateInt() {
        return (int) generateLong();
    }
}
