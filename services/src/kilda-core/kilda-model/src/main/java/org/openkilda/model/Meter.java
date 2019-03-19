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

package org.openkilda.model;

import lombok.Data;

import java.io.Serializable;

@Data
public final class Meter implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final double MAX_NOVIFLOW_BURST_COEFFICIENT = 1.005;
    private static final long MIN_CENTEC_SWITCH_BURST_SIZE = 1024L;
    private static final long MAX_CENTEC_SWITCH_BURST_SIZE = 32000L;

    private static final String[] METER_FLAGS = {"KBPS", "BURST", "STATS"};

    private SwitchId switchId;

    private MeterId meterId;

    /**
     * Calculate burst size using combined (manufacturer + software) switch description.
     */
    public static long calculateBurstSize(long bandwidth, long flowMeterMinBurstSizeInKbits,
                                          double flowMeterBurstCoefficient, String switchDescription) {
        return calculateBurstSize(bandwidth, flowMeterMinBurstSizeInKbits, flowMeterBurstCoefficient,
                switchDescription, switchDescription);
    }

    /**
     * Calculate burst size using switch manufacturer and software description.
     */
    public static long calculateBurstSize(long bandwidth, long flowMeterMinBurstSizeInKbits,
                                          double flowMeterBurstCoefficient, String switchManufacturerDescription,
                                          String switchSoftwareDescription) {
        if (Switch.isCentecSwitch(switchManufacturerDescription)) {
            long burstSize = Math.max(flowMeterMinBurstSizeInKbits, (long) (bandwidth * flowMeterBurstCoefficient));
            if (burstSize < MIN_CENTEC_SWITCH_BURST_SIZE) {
                return MIN_CENTEC_SWITCH_BURST_SIZE;
            }
            return Math.min(burstSize, MAX_CENTEC_SWITCH_BURST_SIZE);
        }

        double burstCoefficient = flowMeterBurstCoefficient;
        if (Switch.isNoviflowSwitch(switchSoftwareDescription) && burstCoefficient > MAX_NOVIFLOW_BURST_COEFFICIENT) {
            burstCoefficient = MAX_NOVIFLOW_BURST_COEFFICIENT;
        }

        return (long) (bandwidth * burstCoefficient);
    }

    /**
     * Get meter flags as string array.
     */
    public static String[] getMeterFlags() {
        return METER_FLAGS;
    }
}
