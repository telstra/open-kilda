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

package org.openkilda.floodlight.command.rulemanager;

import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.rulemanager.report.InstallSpeakerCommandReport;
import org.openkilda.floodlight.converter.OfFlowModConverter;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerCommandData;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class InstallFlowSpeakerCommand extends SpeakerCommand<InstallSpeakerCommandReport> {

    private final FlowSpeakerCommandData commandData;

    public InstallFlowSpeakerCommand(MessageContext messageContext,
                                     SwitchId switchId,
                                     FlowSpeakerCommandData commandData) {
        super(messageContext, switchId);
        this.commandData = commandData;
    }

    @Override
    protected CompletableFuture<InstallSpeakerCommandReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor) {
        OFFlowMod ofFlowMod = OfFlowModConverter.INSTANCE
                .convertInstallCommand(commandData, getSw().getOFFactory());

        CompletableFuture<Optional<OFMessage>> writeResult;
        try (Session session = getSessionService().open(messageContext, getSw())) {
            writeResult = session.write(ofFlowMod);
        }
        return writeResult
                .thenApply(ignore -> makeSuccessReport());
    }

    private InstallSpeakerCommandReport makeSuccessReport() {
        return new InstallSpeakerCommandReport(this);
    }

    @Override
    protected InstallSpeakerCommandReport makeReport(Exception error) {
        return new InstallSpeakerCommandReport(this, error);
    }
}
