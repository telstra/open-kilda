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

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.SpeakerData;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class OfCommandConverter {
    public static final OfCommandConverter INSTANCE = new OfCommandConverter();

    /**
     * Remove dependencies that are absent in the provided list of commands.
     */
    public List<OfCommand> removeExcessDependencies(List<OfCommand> source) {
        Set<UUID> allUuids = toSpeakerData(source).keySet();
        return source.stream()
                .map(command ->
                        recreateCommandWithDependencies(command, data -> {
                            if (allUuids.containsAll(data.getDependsOn())) {
                                return data.getDependsOn();
                            } else {
                                Set<UUID> resultDependsOn = new HashSet<>(data.getDependsOn());
                                resultDependsOn.retainAll(allUuids);
                                return resultDependsOn;
                            }
                        }))
                .collect(toList());
    }

    /**
     * Reverse dependencies as required for deletion.
     */
    public List<OfCommand> reverseDependenciesForDeletion(List<OfCommand> source) {
        Map<UUID, Set<UUID>> reversedDependencies = new HashMap<>();
        toSpeakerData(source).forEach((uuid, data) -> {
            data.getDependsOn().forEach(dependent ->
                    reversedDependencies.computeIfAbsent(dependent, k -> new HashSet<>()).add(uuid));
        });
        return source.stream()
                .map(command ->
                        recreateCommandWithDependencies(command,
                                data -> reversedDependencies.getOrDefault(data.getUuid(), emptySet())))
                .collect(toList());
    }

    /**
     * Get speakerData from the list of OF commands.
     */
    public Map<UUID, SpeakerData> toSpeakerData(List<OfCommand> source) {
        return source.stream()
                .map(command -> {
                    if (command instanceof FlowCommand) {
                        return ((FlowCommand) command).getData();
                    } else if (command instanceof MeterCommand) {
                        return ((MeterCommand) command).getData();
                    } else if (command instanceof GroupCommand) {
                        return ((GroupCommand) command).getData();
                    } else {
                        throw new IllegalArgumentException("Unknown speaker command type: " + command);
                    }
                })
                .collect(toMap(SpeakerData::getUuid, Function.identity()));
    }

    /**
     * Build a list of OF commands from speakerData.
     */
    public List<OfCommand> toOfCommands(List<SpeakerData> speakerData) {
        return speakerData.stream()
                .map(data -> {
                    if (data instanceof GroupSpeakerData) {
                        return new GroupCommand((GroupSpeakerData) data);
                    }
                    if (data instanceof MeterSpeakerData) {
                        return new MeterCommand((MeterSpeakerData) data);
                    }
                    if (data instanceof FlowSpeakerData) {
                        return new FlowCommand((FlowSpeakerData) data);
                    } else {
                        throw new IllegalArgumentException("Unsupported speaker data: " + data);
                    }
                })
                .collect(toList());
    }

    private OfCommand recreateCommandWithDependencies(OfCommand command,
                                                      Function<SpeakerData, Collection<UUID>> newDependencies) {
        if (command instanceof FlowCommand) {
            FlowSpeakerData data = ((FlowCommand) command).getData();
            return new FlowCommand(data.toBuilder().dependsOn(newDependencies.apply(data)).build());
        } else if (command instanceof MeterCommand) {
            MeterSpeakerData data = ((MeterCommand) command).getData();
            return new MeterCommand(data.toBuilder().dependsOn(newDependencies.apply(data)).build());
        } else if (command instanceof GroupCommand) {
            GroupSpeakerData data = ((GroupCommand) command).getData();
            return new GroupCommand(data.toBuilder().dependsOn(newDependencies.apply(data)).build());
        } else {
            throw new IllegalArgumentException("Unknown speaker command type: " + command);
        }
    }

}
