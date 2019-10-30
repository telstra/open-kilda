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
import java.util.Set;

@Data
public final class Meter implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final double MAX_NOVIFLOW_BURST_COEFFICIENT = 1.005;
    private static final long MIN_CENTEC_SWITCH_BURST_SIZE = 1024L;
    private static final long MAX_CENTEC_SWITCH_BURST_SIZE = 32000L;
    public static final int MIN_RATE_IN_KBPS = 64;

    private static final int METER_BURST_SIZE_EQUALS_EPS = 1;

    private static final String[] METER_KBPS_FLAGS = {"KBPS", "BURST", "STATS"};
    private static final String[] METER_PKTPS_FLAGS = {"PKTPS", "BURST", "STATS"};

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
            long burstSize = Math.max(flowMeterMinBurstSizeInKbits, Math.round(bandwidth * flowMeterBurstCoefficient));
            if (burstSize < MIN_CENTEC_SWITCH_BURST_SIZE) {
                return MIN_CENTEC_SWITCH_BURST_SIZE;
            }
            return Math.min(burstSize, MAX_CENTEC_SWITCH_BURST_SIZE);
        }

        double burstCoefficient = flowMeterBurstCoefficient;
        if (Switch.isNoviflowSwitch(switchSoftwareDescription) && burstCoefficient > MAX_NOVIFLOW_BURST_COEFFICIENT) {
            burstCoefficient = MAX_NOVIFLOW_BURST_COEFFICIENT;
        }

        return Math.round(bandwidth * burstCoefficient);
    }

    /**
     * Calculate burst size considering hardware limitations.
     * Noviflow switches round burst size if burst size > rate * 1.005.
     */
    public static long calculateBurstSizeConsideringHardwareLimitations(
            long bandwidth, long requestedBurstSize, Set<SwitchFeature> features) {

        if (features.contains(SwitchFeature.MAX_BURST_COEFFICIENT_LIMITATION)) {
            return Math.min(requestedBurstSize, Math.round(bandwidth * MAX_NOVIFLOW_BURST_COEFFICIENT));
        }

        return requestedBurstSize;
    }

    /**
     * Convert rate from packets to kilobits.
     */
    public static long convertRateToKiloBits(long rateInPackets, long packetSizeInBytes) {
        return Math.max(MIN_RATE_IN_KBPS, (rateInPackets * packetSizeInBytes * 8) / 1024L);
    }

    /**
     * Convert burst size from packets to kilobits.
     */
    public static long convertBurstSizeToKiloBits(long burstSizeInPackets, long packetSizeInBytes) {
        return (burstSizeInPackets * packetSizeInBytes * 8) / 1024L;
    }

    /**
     * Get meter kbps flags as string array.
     */
    public static String[] getMeterKbpsFlags() {
        return METER_KBPS_FLAGS;
    }

    public static String[] getMeterPktpsFlags() {
        return METER_PKTPS_FLAGS;
    }

    /**
     * Compare burst size.
     */
    public static boolean equalsBurstSize(long first, long second) {
        return Math.abs(first - second) <= METER_BURST_SIZE_EQUALS_EPS;
    }
}
