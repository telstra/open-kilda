/* Copyright 2019 Telstra Open Source
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

package org.openkilda.model.of;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Value
@Builder
public class MeterSchemaBand {
    private static final float INACCURATE_RATE_DEVIATION = 0.01f;
    private static final float INACCURATE_BURST_DEVIATION = 0.01f;

    private final int type;

    private final Long rate;       // type: drop
    private final Long burstSize;  // type: drop

    private final boolean inaccurate;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MeterSchemaBand that = (MeterSchemaBand) o;

        EqualsBuilder equals = new EqualsBuilder()
                .append(type, that.type);

        if (inaccurate || that.inaccurate) {
            return equals.isEquals()
                    && inaccurateEquals(rate, that.rate, INACCURATE_RATE_DEVIATION)
                    && inaccurateEquals(burstSize, that.burstSize, INACCURATE_BURST_DEVIATION);
        } else {
            return equals.append(rate, that.rate)
                    .append(burstSize, that.burstSize)
                    .isEquals();
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(type)
                .append(rate)
                .append(burstSize)
                .toHashCode();
    }

    private boolean inaccurateEquals(long left, long right, float deviation) {
        long diff;
        long max;
        if (left < right) {
            max = right;
            diff = right - left;
        } else {
            max = left;
            diff = left - right;
        }
        return diff < max * deviation;
    }
}
