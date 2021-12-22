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

import org.openkilda.rulemanager.SpeakerCommandData;

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
    public static List<SpeakerCommandData> postProcessCommands(List<SpeakerCommandData> commands) {
        commands = removeDuplicateCommands(commands);
        checkCircularDependencies(commands);
        return sortCommandsByDependencies(commands);
    }

    @VisibleForTesting
    static List<SpeakerCommandData> removeDuplicateCommands(List<SpeakerCommandData> commands) {
        return new ArrayList<>(new HashSet<>(commands));
    }

    @VisibleForTesting
    static void checkCircularDependencies(List<SpeakerCommandData> commands) {
        Map<String, Color> used = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();
        Map<String, SpeakerCommandData> commandMap = buildCommandMap(commands);

        for (SpeakerCommandData command : commands) {
            if (!used.containsKey(command.getUuid())) {
                List<SpeakerCommandData> cycle = findCycle(command, commandMap, used, predecessors);
                if (!cycle.isEmpty()) {
                    throw new IllegalStateException(format("Commands has following dependencies cycle: %s", cycle));
                }
            }
        }
    }

    private static Map<String, SpeakerCommandData> buildCommandMap(List<SpeakerCommandData> commands) {
        Map<String, SpeakerCommandData> map = commands.stream()
                .collect(toMap(SpeakerCommandData::getUuid, Function.identity()));
        if (map.size() != commands.size()) {
            for (SpeakerCommandData command : commands) {
                if (map.get(command.getUuid()) != command) {
                    throw new IllegalStateException(format("Commands %s and %s has same UUID '%s'",
                            command, map.get(command.getUuid()), command.getUuid()));
                }
            }
        }
        return map;
    }

    private static List<SpeakerCommandData> findCycle(SpeakerCommandData current,
            Map<String, SpeakerCommandData> commandMap, Map<String, Color> used, Map<String, String> predecessors) {
        used.put(current.getUuid(), Color.GREY);
        for (String nextUuid : current.getDependsOn()) {
            if (!commandMap.containsKey(nextUuid)) {
                throw new IllegalStateException(format(
                        "Command %s depends on unknown command with UUID %s", current, nextUuid));
            }
            if (!used.containsKey(nextUuid)) {
                predecessors.put(nextUuid, current.getUuid());
                List<SpeakerCommandData> cycle = findCycle(commandMap.get(nextUuid), commandMap, used, predecessors);

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

    private static List<SpeakerCommandData> buildCycle(
            String current, String end, Map<String, String> predecessors, Map<String, SpeakerCommandData> commandMap) {
        List<SpeakerCommandData> cycle = new ArrayList<>();
        cycle.add(commandMap.get(current));

        while (!current.equals(end)) {
            current = predecessors.get(current);
            cycle.add(commandMap.get(current));
        }
        return cycle;
    }

    @VisibleForTesting
    static List<SpeakerCommandData> sortCommandsByDependencies(List<SpeakerCommandData> commands) {
        List<SpeakerCommandData> sortedCommands = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Map<String, SpeakerCommandData> commandMap = buildCommandMap(commands);

        for (SpeakerCommandData command : commands) {
            if (!used.contains(command.getUuid())) {
                topologicalSort(command, commandMap, used, sortedCommands);
            }
        }
        return sortedCommands;
    }

    private static void topologicalSort(
            SpeakerCommandData current, Map<String, SpeakerCommandData> commandMap, Set<String> used,
            List<SpeakerCommandData> sortedCommands) {
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
