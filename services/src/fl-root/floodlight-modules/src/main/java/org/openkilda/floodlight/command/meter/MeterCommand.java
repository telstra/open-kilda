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

import org.openkilda.floodlight.command.OfCommand;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.Switch.Feature;
import org.openkilda.model.SwitchId;

import lombok.AllArgsConstructor;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

abstract class MeterCommand extends OfCommand {

    Long meterId;

    MeterCommand(SwitchId switchId, MessageContext messageContext, Long meterId) {
        super(switchId, messageContext);
        this.meterId = meterId;
    }

    void checkSwitchSupportCommand(IOFSwitch sw, FeatureDetectorService featureDetectorService)
            throws UnsupportedSwitchOperationException {
        Set<Feature> supportedFeatures = featureDetectorService.detectSwitch(sw);
        if (!supportedFeatures.contains(Feature.METERS)) {
            throw new UnsupportedSwitchOperationException(sw.getId(), "Switch doesn't support meters");
        }
    }

    @AllArgsConstructor
    final class MeterChecker implements Function<OFMeterConfigStatsReply, Boolean> {
        private final OFMeterMod expectedMeterConfig;

        @Override
        public Boolean apply(OFMeterConfigStatsReply meterConfig) {
            return meterConfig.getEntries()
                    .stream()
                    .filter(entry -> entry.getMeterId() == meterId)
                    .allMatch(entry -> entry.getFlags().containsAll(expectedMeterConfig.getFlags())
                            && meterBandsEqual(entry.getEntries()));
        }

        private boolean meterBandsEqual(List<OFMeterBand> actual) {
            OFMeterBandDrop expectedDrop = getMeterBands()
                    .stream()
                    .filter(OFMeterBandDrop.class::isInstance)
                    .map(OFMeterBandDrop.class::cast)
                    .findFirst()
                    .orElse(null);

            OFMeterBandDrop actualDrop = actual
                    .stream()
                    .filter(OFMeterBandDrop.class::isInstance)
                    .map(OFMeterBandDrop.class::cast)
                    .findFirst()
                    .orElse(null);

            return expectedDrop != null && actualDrop != null && expectedDrop.getRate() == actualDrop.getRate();
        }

        private List<OFMeterBand> getMeterBands() {
            return expectedMeterConfig.getVersion() == OFVersion.OF_13
                    ? expectedMeterConfig.getMeters() : expectedMeterConfig.getBands();
        }
    }
}
