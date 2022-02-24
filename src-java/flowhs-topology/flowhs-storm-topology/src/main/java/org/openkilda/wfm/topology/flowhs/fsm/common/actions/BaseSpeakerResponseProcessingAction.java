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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class BaseSpeakerResponseProcessingAction<T extends
        FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E, C> extends HistoryRecordingAction<T, S, E, C> {

    protected List<OfCommand> removeExcessDependencies(List<OfCommand> source) {
        Set<UUID> allUuids = source.stream().map(command -> {
            if (command instanceof FlowCommand) {
                return ((FlowCommand) command).getData().getUuid();
            } else if (command instanceof MeterCommand) {
                return ((MeterCommand) command).getData().getUuid();
            } else if (command instanceof GroupCommand) {
                return ((GroupCommand) command).getData().getUuid();
            } else {
                throw new IllegalArgumentException("Unknown speaker command type: " + command);
            }
        }).collect(Collectors.toSet());
        return source.stream()
                .map(command -> recreateCommandWithDependedncies(command, allUuids))
                .collect(toList());
    }

    private OfCommand recreateCommandWithDependedncies(OfCommand command, Set<UUID> allUuids) {
        if (command instanceof FlowCommand) {
            FlowSpeakerData data = ((FlowCommand) command).getData();
            if (allUuids.containsAll(data.getDependsOn())) {
                return command;
            } else {
                Set<UUID> resultDependsOn = new HashSet<>(data.getDependsOn());
                resultDependsOn.retainAll(allUuids);
                return new FlowCommand(data.toBuilder().dependsOn(resultDependsOn).build());
            }
        } else if (command instanceof MeterCommand) {
            MeterSpeakerData data = ((MeterCommand) command).getData();
            if (allUuids.containsAll(data.getDependsOn())) {
                return command;
            } else {
                Set<UUID> resultDependsOn = new HashSet<>(data.getDependsOn());
                resultDependsOn.retainAll(allUuids);
                return new MeterCommand(data.toBuilder().dependsOn(resultDependsOn).build());
            }
        } else if (command instanceof GroupCommand) {
            GroupSpeakerData data = ((GroupCommand) command).getData();
            if (allUuids.containsAll(data.getDependsOn())) {
                return command;
            } else {
                Set<UUID> resultDependsOn = new HashSet<>(data.getDependsOn());
                resultDependsOn.retainAll(allUuids);
                return new GroupCommand(data.toBuilder().dependsOn(resultDependsOn).build());
            }
        } else {
            throw new IllegalArgumentException("Unknown speaker command type: " + command);
        }
    }
}
