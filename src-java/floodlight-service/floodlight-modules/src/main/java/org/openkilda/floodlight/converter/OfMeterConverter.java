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

import org.openkilda.messaging.info.meter.MeterEntry;

import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.Optional;

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
        Optional<OFMeterBandDrop> meterBandDrop = entry.getEntries().stream()
                .filter(ofMeterBand -> ofMeterBand instanceof OFMeterBandDrop)
                .findAny()
                .map(ofMeterBand -> (OFMeterBandDrop) ofMeterBand);
        return MeterEntry.builder()
                .version(entry.getVersion().toString())
                .meterId(entry.getMeterId())
                .rate(meterBandDrop.map(OFMeterBandDrop::getRate).orElse((long) 0))
                .burstSize(meterBandDrop.map(OFMeterBandDrop::getBurstSize).orElse((long) 0))
                .flags(entry.getFlags().stream()
                        .map(OFMeterFlags::name)
                        .toArray(String[]::new))
                .build();
    }
}
