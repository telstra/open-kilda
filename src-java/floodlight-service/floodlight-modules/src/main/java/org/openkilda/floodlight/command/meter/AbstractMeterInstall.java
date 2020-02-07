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

package org.openkilda.floodlight.command.meter;

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;

import java.util.List;
import java.util.Set;

@Getter
abstract class AbstractMeterInstall<T extends SpeakerCommandReport> extends MeterCommand<T> {
    // payload
    protected final MeterConfig meterConfig;

    AbstractMeterInstall(MessageContext messageContext, SwitchId switchId, MeterConfig meterConfig) {
        super(messageContext, switchId);
        this.meterConfig = meterConfig;
    }

    protected OFMeterMod makeMeterAddMessage() {
        final OFFactory ofFactory = getSw().getOFFactory();

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterConfig.getId().getValue())
                .setCommand(OFMeterModCommand.ADD)
                .setFlags(makeMeterFlags());

        // NB: some switches might replace 0 burst size value with some predefined value
        List<OFMeterBand> meterBand = makeMeterBands();
        if (ofFactory.getVersion().compareTo(OF_13) > 0) {
            meterModBuilder.setBands(meterBand);
        } else {
            meterModBuilder.setMeters(meterBand);
        }

        return meterModBuilder.build();
    }

    Set<OFMeterFlags> makeMeterFlags() {
        return ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.BURST, OFMeterFlags.STATS);
    }

    List<OFMeterBand> makeMeterBands() {
        SwitchManagerConfig switchManagerConfig = getSwitchManagerConfig();
        long burstSize = Meter.calculateBurstSize(
                meterConfig.getBandwidth(), switchManagerConfig.getFlowMeterMinBurstSizeInKbits(),
                switchManagerConfig.getFlowMeterBurstCoefficient(),
                getSw().getSwitchDescription().getManufacturerDescription(),
                getSw().getSwitchDescription().getSoftwareDescription());
        return makeMeterBands(burstSize);
    }

    List<OFMeterBand> makeMeterBands(long burstSize) {
        return ImmutableList.of(getSw().getOFFactory().meterBands()
                .buildDrop()
                .setRate(meterConfig.getBandwidth())
                .setBurstSize(burstSize)
                .build());
    }
}
