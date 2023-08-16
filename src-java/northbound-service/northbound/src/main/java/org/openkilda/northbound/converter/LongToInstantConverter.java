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

package org.openkilda.northbound.converter;

import java.time.Instant;

/**
 * This class converts Linux epoch seconds or milliseconds to an Instant object.
 */
public final class LongToInstantConverter {
    private LongToInstantConverter() {
    }

    /**
     * Converts Linux epoch seconds or milliseconds to an Instant object.
     * <p>
     * When the input has 10 digits in magnitude (so log base 10 of this number is less than 10),
     * we assume that this is epoch time in seconds, e.g. 1692183083
     * When the input has more than 10 digits in magnitude (so log base 10 of this number is greater
     * than 10), we assume that this is epoch time in milliseconds, e.g. 1692183083123. </p>
     * <p>
     * For the inputs 1692183083 and 1692183083000, this method produces equal Instant representations.
     * </p>
     * It is assumed that the input time will be some point in 21st century.
     * @param timeAsLong non-null value that represents Linux epoch time in seconds or milliseconds
     * @return an Instant object
     */
    public static Instant convert(Long timeAsLong) {
        if (timeAsLong == null) {
            throw new IllegalArgumentException();
        }

        if (Math.log10(Math.abs(timeAsLong)) < 10) {
            return Instant.ofEpochSecond(timeAsLong);
        } else {
            return Instant.ofEpochMilli(timeAsLong);
        }
    }
}
