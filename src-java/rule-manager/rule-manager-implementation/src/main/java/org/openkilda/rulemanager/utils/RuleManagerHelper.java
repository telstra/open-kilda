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

package org.openkilda.rulemanager.utils;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;

import org.openkilda.rulemanager.SpeakerData;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class RuleManagerHelper {
    private RuleManagerHelper() {
    }

    /**
     * Filter, validate and sort Speaker commands which were created by SwitchManager.
     *
     * <p>Removes all duplicate commands (commands list can contain several equal shared rules).
     * Checks that commands have no circular dependencies.
     * Put commands which have dependencies to the end of the list:
     * If command A (with position i) depends on command B (with position j) than j < i. Where i and j are command
     * indexes in result command list.
     */
    public static List<SpeakerData> postProcessCommands(List<SpeakerData> commands) {
        commands = removeDuplicateCommands(commands);
        checkCircularDependencies(commands);
        return sortCommandsByDependencies(commands);
    }

    @VisibleForTesting
    static List<SpeakerData> removeDuplicateCommands(List<SpeakerData> commands) {
        return new ArrayList<>(new HashSet<>(commands));
    }

    @VisibleForTesting
    static void checkCircularDependencies(List<SpeakerData> commands) {
        Map<String, Color> used = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();
        Map<String, SpeakerData> commandMap = buildCommandMap(commands);

        for (SpeakerData command : commands) {
            if (!used.containsKey(command.getUuid())) {
                List<SpeakerData> cycle = findCycle(command, commandMap, used, predecessors);
                if (!cycle.isEmpty()) {
                    throw new IllegalStateException(format("Commands has following dependencies cycle: %s", cycle));
                }
            }
        }
    }

    private static Map<String, SpeakerData> buildCommandMap(List<SpeakerData> commands) {
        Map<String, SpeakerData> map = commands.stream()
                .collect(toMap(SpeakerData::getUuid, Function.identity()));
        if (map.size() != commands.size()) {
            for (SpeakerData command : commands) {
                if (map.get(command.getUuid()) != command) {
                    throw new IllegalStateException(format("Commands %s and %s has same UUID '%s'",
                            command, map.get(command.getUuid()), command.getUuid()));
                }
            }
        }
        return map;
    }

    private static List<SpeakerData> findCycle(SpeakerData current,
                                               Map<String, SpeakerData> commandMap, Map<String, Color> used,
                                               Map<String, String> predecessors) {
        used.put(current.getUuid(), Color.GREY);
        for (String nextUuid : current.getDependsOn()) {
            if (!commandMap.containsKey(nextUuid)) {
                throw new IllegalStateException(format(
                        "Command %s depends on unknown command with UUID %s", current, nextUuid));
            }
            if (!used.containsKey(nextUuid)) {
                predecessors.put(nextUuid, current.getUuid());
                List<SpeakerData> cycle = findCycle(commandMap.get(nextUuid), commandMap, used, predecessors);

                if (!cycle.isEmpty()) {
                    return cycle;
                }
            } else if (Color.GREY.equals(used.get(nextUuid))) {
                return buildCycle(current.getUuid(), nextUuid, predecessors, commandMap);
            }
        }
        used.put(current.getUuid(), Color.BLACK);
        return new ArrayList<>();
    }

    private static List<SpeakerData> buildCycle(
            String current, String end, Map<String, String> predecessors, Map<String, SpeakerData> commandMap) {
        List<SpeakerData> cycle = new ArrayList<>();
        cycle.add(commandMap.get(current));

        while (!current.equals(end)) {
            current = predecessors.get(current);
            cycle.add(commandMap.get(current));
        }
        return cycle;
    }

    @VisibleForTesting
    static List<SpeakerData> sortCommandsByDependencies(List<SpeakerData> commands) {
        List<SpeakerData> sortedCommands = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Map<String, SpeakerData> commandMap = buildCommandMap(commands);

        for (SpeakerData command : commands) {
            if (!used.contains(command.getUuid())) {
                topologicalSort(command, commandMap, used, sortedCommands);
            }
        }
        return sortedCommands;
    }

    private static void topologicalSort(
            SpeakerData current, Map<String, SpeakerData> commandMap, Set<String> used,
            List<SpeakerData> sortedCommands) {
        used.add(current.getUuid());

        for (String nextUuid : current.getDependsOn()) {
            if (!used.contains(nextUuid)) {
                topologicalSort(commandMap.get(nextUuid), commandMap, used, sortedCommands);
            }
        }
        sortedCommands.add(current);
    }

    private enum Color {
        GREY, BLACK
    }
}
