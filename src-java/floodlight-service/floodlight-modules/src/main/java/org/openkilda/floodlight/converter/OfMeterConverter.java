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

package org.openkilda.floodlight.converter;

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.openkilda.messaging.info.meter.MeterEntry;

import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class that converts OFMeterConfig from the switch to kilda known format for further processing.
 */
public final class OfMeterConverter {

    private OfMeterConverter() {}

    /**
     * Convert {@link OFMeterConfig} to format that kilda supports.
     * @param entry meter entry to be converted.
     * @return result of transformation.
     */
    public static MeterEntry toMeterEntry(OFMeterConfig entry) {
        return getMeterEntry(entry.getEntries(), entry.getVersion(), entry.getMeterId(), entry.getFlags());
    }

    /**
     * Convert {@link OFMeterMod} to format that kilda supports.
     * @param entry meter entry to be converted.
     * @return result of transformation.
     */
    public static MeterEntry toMeterEntry(OFMeterMod entry) {
        List<OFMeterBand> ofMeterBands = entry.getVersion().compareTo(OF_13) > 0
                ? entry.getBands() : entry.getMeters();
        return getMeterEntry(ofMeterBands, entry.getVersion(), entry.getMeterId(), entry.getFlags());
    }

    private static MeterEntry getMeterEntry(List<OFMeterBand> meterBands, OFVersion version, long meterId,
                                            Set<OFMeterFlags> flags) {
        Optional<OFMeterBandDrop> meterBandDrop = meterBands.stream()
                .filter(OFMeterBandDrop.class::isInstance)
                .findAny()
                .map(OFMeterBandDrop.class::cast);
        return MeterEntry.builder()
                .version(version.toString())
                .meterId(meterId)
                .rate(meterBandDrop.map(OFMeterBandDrop::getRate).orElse((long) 0))
                .burstSize(meterBandDrop.map(OFMeterBandDrop::getBurstSize).orElse((long) 0))
                .flags(flags.stream()
                        .map(OFMeterFlags::name)
                        .toArray(String[]::new))
                .build();
    }
}
