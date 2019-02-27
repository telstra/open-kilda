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

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.MessageWriter;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;

import java.util.Collections;
import java.util.List;

public class RemoveMeterCommand extends MeterCommand {

    public RemoveMeterCommand(@JsonProperty("message_context") MessageContext messageContext,
                              @JsonProperty("switch_id") SwitchId switchId,
                              @JsonProperty("meter_id") Long meterId) {
        super(switchId, messageContext, meterId);
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    protected FloodlightResponse buildResponse() {
        return null;
    }

    @Override
    public List<MessageWriter> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws UnsupportedSwitchOperationException {
        FeatureDetectorService featureDetector = moduleContext.getServiceImpl(FeatureDetectorService.class);
        checkSwitchSupportCommand(sw, featureDetector);

        OFMeterMod meterDelete = sw.getOFFactory().buildMeterMod()
                .setMeterId(meterId)
                .setCommand(OFMeterModCommand.DELETE)
                .build();

        return Collections.singletonList(new MessageWriter(meterDelete));
    }
}
