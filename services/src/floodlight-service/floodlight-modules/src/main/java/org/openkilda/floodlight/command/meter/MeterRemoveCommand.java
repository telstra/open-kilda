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

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;

import java.util.concurrent.CompletableFuture;

public class MeterRemoveCommand extends MeterBlankCommand {
    public MeterRemoveCommand(MessageContext messageContext, SwitchId switchId, MeterConfig meterConfig) {
        super(messageContext, switchId, meterConfig);
    }

    @Override
    protected CompletableFuture<MeterReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor) throws Exception {
        ensureSwitchSupportMeters();

        IOFSwitch sw = getSw();
        MeterId meterId = meterConfig.getId();
        OFMeterMod meterDeleteMessage = sw.getOFFactory().buildMeterMod()
                .setMeterId(meterId.getValue())
                .setCommand(OFMeterModCommand.DELETE)
                .build();
        try (Session session = getSessionService().open(messageContext, sw)) {
            return session.write(meterDeleteMessage)
                    .thenApply(ignore -> new MeterReport(meterId));
        }
    }
}
