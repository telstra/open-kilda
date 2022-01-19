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

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Value
@SuperBuilder
@JsonNaming(SnakeCaseStrategy.class)
public class MeterSpeakerData extends SpeakerData {

    private static final long INACCURATE_RATE_ALLOWED_DEVIATION = 1;
    private static final long INACCURATE_BURST_ALLOWED_DEVIATION = 1;
    private static final float INACCURATE_RATE_ALLOWED_RELATIVE_DEVIATION = 0.01f;
    private static final float INACCURATE_BURST_ALLOWED_RELATIVE_DEVIATION = 0.01f;
    private static final long ACCURATE_BURST_ALLOWED_DEVIATION = 1;

    MeterId meterId;
    long rate;
    long burst;
    Set<MeterFlag> flags;
    boolean inaccurate;

    @JsonCreator
    public MeterSpeakerData(@JsonProperty("uuid") UUID uuid,
                            @JsonProperty("switch_id") SwitchId switchId,
                            @JsonProperty("depends_on") Collection<UUID> dependsOn,
                            @JsonProperty("of_version") OfVersion ofVersion,
                            @JsonProperty("meter_id") MeterId meterId,
                            @JsonProperty("rate") long rate,
                            @JsonProperty("burst") long burst,
                            @JsonProperty("flags") Set<MeterFlag> flags,
                            @JsonProperty("inaccurate") boolean inaccurate) {
        super(uuid, switchId, dependsOn, ofVersion);
        this.meterId = meterId;
        this.rate = rate;
        this.burst = burst;
        this.flags = flags;
        this.inaccurate = inaccurate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MeterSpeakerData that = (MeterSpeakerData) o;
        if (!Objects.equals(meterId, that.meterId) || !Objects.equals(flags, that.flags)
                || this.inaccurate != that.inaccurate) {
            return false;
        }
        if (this.inaccurate) {
            return isEqualOrWithinDeviation(rate, that.rate, INACCURATE_RATE_ALLOWED_DEVIATION,
                    INACCURATE_RATE_ALLOWED_RELATIVE_DEVIATION)
                    && isEqualOrWithinDeviation(burst, that.burst, INACCURATE_BURST_ALLOWED_DEVIATION,
                    INACCURATE_BURST_ALLOWED_RELATIVE_DEVIATION);
        } else {
            return rate == that.rate && inaccurateEquals(burst, that.burst, INACCURATE_BURST_ALLOWED_DEVIATION);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId, rate, burst, flags, inaccurate);
    }

    private boolean isEqualOrWithinDeviation(long left, long right,
                                             long allowedDeviation, float allowedRelativeDeviation) {
        return inaccurateEquals(left, right, allowedDeviation)
                || inaccurateEquals(left, right, allowedRelativeDeviation);
    }

    private boolean inaccurateEquals(long left, long right, long deviation) {
        return Math.abs(right - left) <= deviation;
    }

    private boolean inaccurateEquals(long left, long right, float deviation) {
        return Math.abs(right - left) < Math.max(right, left) * deviation;
    }
}
