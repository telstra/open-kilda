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

package org.openkilda.floodlight.converter.rulemanager;

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.openkilda.model.MeterId;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;

import com.google.common.collect.ImmutableList;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public class OfMeterConverter {
    public static final OfMeterConverter INSTANCE = Mappers.getMapper(OfMeterConverter.class);

    private Set<OFMeterFlags> toOfMeterFlags(Set<MeterFlag> origin) {
        Set<OFMeterFlags> flags = new HashSet<>();

        for (MeterFlag flag : origin) {
            switch (flag) {
                case KBPS:
                    flags.add(OFMeterFlags.KBPS);
                    break;
                case BURST:
                    flags.add(OFMeterFlags.BURST);
                    break;
                case PKTPS:
                    flags.add(OFMeterFlags.PKTPS);
                    break;
                case STATS:
                    flags.add(OFMeterFlags.STATS);
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unknown meter flag %s", flag));
            }
        }

        return flags;
    }

    private Set<MeterFlag> fromOfMeterFlags(Set<OFMeterFlags> origin) {
        Set<MeterFlag> flags = new HashSet<>();

        for (OFMeterFlags flag : origin) {
            switch (flag) {
                case KBPS:
                    flags.add(MeterFlag.KBPS);
                    break;
                case BURST:
                    flags.add(MeterFlag.BURST);
                    break;
                case PKTPS:
                    flags.add(MeterFlag.PKTPS);
                    break;
                case STATS:
                    flags.add(MeterFlag.STATS);
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unknown meter flag %s", flag));
            }
        }

        return flags;
    }

    /**
     * Convert Meter Install Command.
     * @param commandData data
     * @param ofFactory factory
     * @return mod
     */
    public OFMeterMod convertInstallMeterCommand(MeterSpeakerData commandData, OFFactory ofFactory) {
        OFMeterMod.Builder builder = ofFactory.buildMeterMod();
        builder.setMeterId(commandData.getMeterId().getValue())
                .setFlags(toOfMeterFlags(commandData.getFlags()));

        builder.setCommand(OFMeterModCommand.ADD);
        ImmutableList<OFMeterBand> bandDrops = ImmutableList.of(ofFactory.meterBands()
                .buildDrop()
                .setRate(commandData.getRate())
                .setBurstSize(commandData.getBurst())
                .build());

        if (ofFactory.getVersion().compareTo(OF_13) > 0) {
            builder.setBands(bandDrops);
        } else {
            builder.setMeters(bandDrops);
        }
        return builder.build();
    }

    /**
     * Convert Meter Delete Command.
     * @param commandData data
     * @param ofFactory factory
     * @return mod
     */
    public OFMeterMod convertDeleteMeterCommand(MeterSpeakerData commandData, OFFactory ofFactory) {
        OFMeterMod.Builder builder = ofFactory.buildMeterMod();
        builder.setCommand(OFMeterModCommand.DELETE);
        builder.setMeterId(commandData.getMeterId().getValue())
                .setFlags(toOfMeterFlags(commandData.getFlags()));


        ImmutableList<OFMeterBand> bandDrops = ImmutableList.of(ofFactory.meterBands()
                .buildDrop()
                .setRate(commandData.getRate())
                .setBurstSize(commandData.getBurst())
                .build());

        if (ofFactory.getVersion().compareTo(OF_13) > 0) {
            builder.setBands(bandDrops);
        } else {
            builder.setMeters(bandDrops);
        }
        return builder.build();
    }

    /**
     * Convert meter stats reply.
     */
    public List<MeterSpeakerData> convertToMeterSpeakerData(OFMeterConfigStatsReply statsReply,
                                                            boolean inaccurate) {
        return statsReply.getEntries().stream()
                .map(entry -> convertToMeterSpeakerData(entry, inaccurate))
                .collect(Collectors.toList());
    }

    /**
     * Convert meter config.
     */
    public MeterSpeakerData convertToMeterSpeakerData(OFMeterConfig meterConfig, boolean inaccurate) {
        MeterId meterId = new MeterId(meterConfig.getMeterId());
        long rate = 0;
        long burst = 0;
        for (OFMeterBand band : meterConfig.getEntries()) {
            if (band instanceof OFMeterBandDrop) {
                rate = ((OFMeterBandDrop) band).getRate();
                burst = ((OFMeterBandDrop) band).getBurstSize();
            }
        }
        return MeterSpeakerData.builder()
                .meterId(meterId)
                .burst(burst)
                .rate(rate)
                .flags(fromOfMeterFlags(meterConfig.getFlags()))
                .inaccurate(inaccurate)
                .build();
    }
}
