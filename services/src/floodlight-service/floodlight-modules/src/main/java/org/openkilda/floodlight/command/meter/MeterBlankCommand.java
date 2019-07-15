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

import org.openkilda.floodlight.converter.MeterSchemaMapper;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.MeterSchema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.NonNull;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;

import java.util.List;
import java.util.Set;

abstract class MeterBlankCommand extends MeterCommand<MeterReport> {
    // payload
    protected final MeterConfig meterConfig;

    MeterBlankCommand(MessageContext messageContext, SwitchId switchId, @NonNull MeterConfig meterConfig) {
        // TODO: get rid from null argument (rework hierarchy/make meter commands "remote")
        super(messageContext, switchId, null);
        this.meterConfig = meterConfig;
    }

    @Override
    protected MeterReport makeReport(Exception error) {
        return new MeterReport(error);
    }

    protected MeterReport makeSuccessReport(OFMeterMod meterMod) {
        return makeSuccessReport(MeterSchemaMapper.INSTANCE.map(getSw().getId(), meterMod));
    }

    protected MeterReport makeSuccessReport(MeterSchema schema) {
        return new MeterReport(schema);
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
