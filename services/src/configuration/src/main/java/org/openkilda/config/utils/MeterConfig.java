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

package org.openkilda.config.utils;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;

import java.util.Set;

public final class MeterConfig {

    private static final Set<OFMeterFlags> METER_FLAGS
            = ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.BURST, OFMeterFlags.STATS);
    private static final long MAX_CENTEC_SWITCH_BURST_SIZE = 32000L;

    /**
     * Calculate burst size.
     */
    public static long calculateBurstSize(long bandwidth, long flowMeterMinBurstSizeInKbits,
                                          double flowMeterBurstCoefficient, String switchManufacturerDescription) {
        if (isCentecSwitch(switchManufacturerDescription)) {
            return Math.min(Math.max(flowMeterMinBurstSizeInKbits,
                    (long) (bandwidth * flowMeterBurstCoefficient)), MAX_CENTEC_SWITCH_BURST_SIZE);
        }

        return Math.max(flowMeterMinBurstSizeInKbits, (long) (bandwidth * flowMeterBurstCoefficient));
    }

    private static  boolean isCentecSwitch(String manufacturerDescription) {
        return StringUtils.contains(manufacturerDescription, "Centec");
    }

    public static Set<OFMeterFlags> getMeterFlags() {
        return METER_FLAGS;
    }

    /**
     * Get meter flags as string array.
     */
    public static String[] getMeterFlagsAsStringArray() {
        return METER_FLAGS.stream()
                .map(OFMeterFlags::name)
                .toArray(String[]::new);
    }

    private MeterConfig() {
        throw new UnsupportedOperationException();
    }
}
