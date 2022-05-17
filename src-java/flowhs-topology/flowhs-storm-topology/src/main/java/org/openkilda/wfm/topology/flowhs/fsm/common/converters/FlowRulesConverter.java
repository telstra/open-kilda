/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common.converters;

import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.request.rulemanager.Origin;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.CommandContext;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public final class FlowRulesConverter {
    public static final FlowRulesConverter INSTANCE = new FlowRulesConverter();

    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    /**
     * Build a list of InstallSpeakerCommandsRequest from the provided speakerData.
     */
    public Collection<InstallSpeakerCommandsRequest> buildFlowInstallCommands(
            Map<SwitchId, List<SpeakerData>> speakerData, CommandContext context, Origin origin) {
        return speakerData.entrySet().stream()
                .map(entry -> {
                    List<OfCommand> ofCommands = OfCommandConverter.INSTANCE.toOfCommands(entry.getValue());
                    return buildFlowInstallCommand(entry.getKey(), ofCommands, context, origin);
                })
                .collect(Collectors.toList());
    }

    /**
     * Build a InstallSpeakerCommandsRequest from the provided OF commands.
     */
    public InstallSpeakerCommandsRequest buildFlowInstallCommand(SwitchId switchId, List<OfCommand> ofCommands,
                                                                 CommandContext context, Origin origin) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return new InstallSpeakerCommandsRequest(messageContext, switchId, commandId, ofCommands, origin);
    }

    /**
     * Build a list of DeleteSpeakerCommandsRequest from the provided speakerData.
     * NOTICE: the given dependencies are reversed as required for deletion.
     */
    public Collection<DeleteSpeakerCommandsRequest> buildFlowDeleteCommands(
            Map<SwitchId, List<SpeakerData>> speakerData, CommandContext context, Origin origin) {
        return speakerData.entrySet().stream()
                .map(entry -> {
                    List<OfCommand> ofCommands = OfCommandConverter.INSTANCE.toOfCommands(entry.getValue());
                    ofCommands = OfCommandConverter.INSTANCE.reverseDependenciesForDeletion(ofCommands);
                    return buildFlowDeleteCommand(entry.getKey(), ofCommands, context, origin);
                })
                .collect(toList());
    }

    /**
     * Build a DeleteSpeakerCommandsRequest from the provided OF commands.
     */
    public DeleteSpeakerCommandsRequest buildFlowDeleteCommand(SwitchId switchId, List<OfCommand> ofCommands,
                                                               CommandContext context, Origin origin) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return new DeleteSpeakerCommandsRequest(messageContext, switchId, commandId, ofCommands, origin);
    }
}
