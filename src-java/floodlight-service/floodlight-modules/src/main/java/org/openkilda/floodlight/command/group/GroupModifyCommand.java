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

package org.openkilda.floodlight.command.group;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.error.InvalidGroupIdException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFGroupMod;

import java.util.concurrent.CompletableFuture;

@Getter
public class GroupModifyCommand extends GroupInstallCommand {

    private SpeakerCommandProcessor commandProcessor;

    public GroupModifyCommand(MessageContext messageContext, SwitchId switchId, MirrorConfig mirrorConfig) {
        super(messageContext, switchId, mirrorConfig);
    }

    @Override
    protected CompletableFuture<GroupInstallReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor)
            throws UnsupportedSwitchOperationException, InvalidGroupIdException {
        this.commandProcessor = commandProcessor;

        ensureSwitchSupportGroups();
        ensureGroupIdValid();

        OFGroupMod groupMod = makeGroupModifyOfMessage();
        return writeSwitchRequest(groupMod)
                .thenApply(ignore -> makeSuccessReport());
    }
}
