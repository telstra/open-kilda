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

import static java.lang.String.format;

import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.rulemanager.report.InstallSpeakerCommandReport;
import org.openkilda.floodlight.command.rulemanager.report.InstallSpeakerCommandsReport;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.SpeakerCommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InstallSpeakerCommands extends SpeakerCommand<InstallSpeakerCommandsReport> {

    private static final int MAX_DEPTH = 5;

    private final Collection<SpeakerCommandData> commandData;

    @JsonCreator
    @Builder(toBuilder = true)
    public InstallSpeakerCommands(@JsonProperty("message_context") MessageContext messageContext,
                                  @JsonProperty("command_id") SwitchId switchId,
                                  @JsonProperty("command_data") Collection<SpeakerCommandData> commandData) {
        super(messageContext, switchId);
        this.commandData = commandData;
    }

    @Override
    protected CompletableFuture<InstallSpeakerCommandsReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor) {
        List<SpeakerCommandData> commandList = sortCommands(commandData);

        CompletableFuture<InstallSpeakerCommandReport>[] tasks = commandList.stream()
                .map(command -> processCommand(command, commandProcessor))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(tasks)
                .thenApply(c -> new InstallSpeakerCommandsReport(this));
    }

    private List<SpeakerCommandData> sortCommands(Collection<SpeakerCommandData> commandData) {
        Set<String> uuids = commandData.stream().map(SpeakerCommandData::getUuid).collect(Collectors.toSet());
        commandData.forEach(command -> command.getDependsOn().retainAll(uuids));

        List<SpeakerCommandData> result = commandData.stream()
                .filter(command -> command.getDependsOn().isEmpty())
                .collect(Collectors.toList());

        int depth = 0;
        while (depth < MAX_DEPTH || result.size() == commandData.size()) {
            Set<String> currentUuids = result.stream().map(SpeakerCommandData::getUuid).collect(Collectors.toSet());
            List<SpeakerCommandData> toAdd = commandData.stream()
                    .filter(command -> currentUuids.containsAll(command.getDependsOn()))
                    .filter(command -> !currentUuids.contains(command.getUuid()))
                    .collect(Collectors.toList());

            result.addAll(toAdd);
            depth++;
        }

        return result;
    }

    private CompletableFuture<InstallSpeakerCommandReport> processCommand(SpeakerCommandData commandData,
                                                                          SpeakerCommandProcessor commandProcessor) {
        if (commandData instanceof FlowSpeakerCommandData) {
            FlowSpeakerCommandData flowSpeakerCommandData = (FlowSpeakerCommandData) commandData;
            InstallFlowSpeakerCommand installCommand = new InstallFlowSpeakerCommand(
                    messageContext, switchId, flowSpeakerCommandData);

            return commandProcessor.chain(installCommand);
        } else {
            // todo (rule-manager-fl-integration): add meter/group commands
            throw new IllegalStateException(format("Unknown command type %s", commandData));
        }
    }

    @Override
    protected InstallSpeakerCommandsReport makeReport(Exception error) {
        return null;
    }
}
