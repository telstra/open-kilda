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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.InsufficientCapabilitiesException;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VerificationDispatchCommand extends AbstractVerificationCommand {
    private static final Logger log = LoggerFactory.getLogger(VerificationDispatchCommand.class);

    private final IOFSwitchService switchService;

    public VerificationDispatchCommand(CommandContext context, UniFlowVerificationRequest verificationRequest) {
        super(context, verificationRequest);

        FloodlightModuleContext moduleContext = getContext().getModuleContext();
        switchService = moduleContext.getServiceImpl(IOFSwitchService.class);
    }

    @Override
    public void run() {
        launchSubCommands(produceSubCommands());
    }

    List<Optional<Command>> produceSubCommands() {
        List<Optional<Command>> plan = new ArrayList<>();

        try {
            // TODO(surabujin): will need to synchronise send/receive readiness in multi FL environment
            plan.add(makeListenCommand());
            plan.add(makeSendCommand());
        } catch (InsufficientCapabilitiesException e) {
            log.error(
                    "Unable to perform flow VERIFICATION due to {} (packetID: {})",
                    e.toString(), getVerificationRequest().getPacketId());
            sendErrorResponse(FlowVerificationErrorCode.NOT_CAPABLE);
            plan.clear();
        }

        return plan;
    }

    private void launchSubCommands(List<Optional<Command>> subCommands) {
        for (Optional<Command> command : subCommands) {
            command.ifPresent(this::startSubCommand);
        }
    }

    private Optional<Command> makeSendCommand() {
        UniFlowVerificationRequest verificationRequest = getVerificationRequest();
        if (!isOwnSwitch(verificationRequest.getSourceSwitchId())) {
            log.debug("Switch {} is not under our control, do not produce flow verification send request");
            return Optional.empty();
        }

        log.debug("Initiate verification send command (request: {})", verificationRequest.getPacketId());
        return Optional.of(new VerificationSendCommand(getContext(), verificationRequest));
    }

    private Optional<Command> makeListenCommand() throws InsufficientCapabilitiesException {
        UniFlowVerificationRequest verificationRequest = getVerificationRequest();
        if (!isOwnSwitch(verificationRequest.getDestSwitchId())) {
            log.debug("Switch {} is not under our control, do not produce flow verification receive handler");
            return Optional.empty();
        }

        log.debug("Initiate verification listen command (request: {})", verificationRequest.getPacketId());
        return Optional.of(new VerificationListenCommand(getContext(), verificationRequest));
    }

    private boolean isOwnSwitch(String switchId) {
        DatapathId dpId = DatapathId.of(switchId);
        IOFSwitch sw = switchService.getActiveSwitch(dpId);

        return sw != null;
    }
}
