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

package org.openkilda.rulemanager;

import static java.lang.String.format;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.match.FieldMatch;

import java.util.List;
import java.util.Set;

/**
 * Testing utils.
 */
public final class Utils {

    /**
     * Build switch object for tests.
     */
    public static Switch buildSwitch(String version, Set<SwitchFeature> features) {
        SwitchId switchId = new SwitchId(1L);
        return Switch.builder()
                .switchId(switchId)
                .ofVersion(version)
                .features(features)
                .build();
    }

    /**
     * Build switch properties object for tests.
     */
    public static SwitchProperties buildSwitchProperties(Switch sw, boolean multiTable) {
        return SwitchProperties.builder()
                .switchObj(sw)
                .multiTable(multiTable)
                .build();
    }

    /**
     * Find Speaker Command Data of specific type.
     */
    public static <C extends SpeakerCommandData> C getCommand(Class<C> commandType,
                                                              List<SpeakerCommandData> commands) {
        return commands.stream()
                .filter(commandType::isInstance)
                .map(commandType::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(format("Can't find command with type %s", commandType)));
    }

    /**
     * Find match on specific field.
     */
    public static FieldMatch getMatchByField(Field field, Set<FieldMatch> match) {
        return match.stream()
                .filter(m -> field == m.getField())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(format("Can't find match on field %s", field)));
    }

    /**
     * Find Action of specific type.
     */
    public static <A extends Action> A getActionByType(Class<A> actionType, Set<Action> actions) {
        return actions.stream()
                .filter(actionType::isInstance)
                .map(actionType::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(format("Can't find action with type %s", actionType)));
    }

    private Utils() {
    }
}
