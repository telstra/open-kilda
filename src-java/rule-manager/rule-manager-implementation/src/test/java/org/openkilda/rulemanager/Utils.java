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
import static java.util.stream.Collectors.toSet;

import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchProperties.RttState;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Lists;
import lombok.Value;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Testing utils.
 */
public final class Utils {

    public static final int SERVER_42_PORT = 42;
    public static final int SERVER_42_VLAN = 142;
    public static final MacAddress SERVER_42_MAC_ADDRESS = new MacAddress("42:42:42:42:42:42");
    public static final SwitchId SWITCH_ID = new SwitchId(1L);
    public static final int LAG_PORT_NUMBER_1 = 1;
    public static final int LAG_PORT_NUMBER_2 = 2;
    public static final int LAG_PORT_NUMBER_3 = 3;
    public static final int PHYS_PORT_1 = 3;
    public static final int PHYS_PORT_2 = 3;
    public static final int PHYS_PORT_3 = 3;
    public static final int PHYS_PORT_4 = 3;

    public static final LagLogicalPort LAG_PORT_1 = new LagLogicalPort(SWITCH_ID,  LAG_PORT_NUMBER_1,
            Lists.newArrayList(PHYS_PORT_1, PHYS_PORT_2), true);
    public static final LagLogicalPort LAG_PORT_2 = new LagLogicalPort(SWITCH_ID,  LAG_PORT_NUMBER_2,
            new ArrayList<Integer>(), true);
    public static final LagLogicalPort LAG_PORT_3 = new LagLogicalPort(SWITCH_ID,  LAG_PORT_NUMBER_3,
            Lists.newArrayList(PHYS_PORT_3, PHYS_PORT_4), false);
    public static final List<LagLogicalPort> LAG_PORTS = Lists.newArrayList(LAG_PORT_1, LAG_PORT_2, LAG_PORT_3);


    /**
     * Build switch object for tests.
     */
    public static Switch buildSwitch(SwitchId switchId, String version, Set<SwitchFeature> features) {
        return Switch.builder()
                .switchId(switchId)
                .ofVersion(version)
                .features(features)
                .ofDescriptionManufacturer("Nikara")
                .ofDescriptionSoftware("2.15.0")
                .build();
    }

    public static Switch buildSwitch(String version, Set<SwitchFeature> features) {
        return buildSwitch(SWITCH_ID, version, features);
    }

    public static Switch buildSwitch(SwitchId switchId, Set<SwitchFeature> features) {
        return buildSwitch(switchId, "OF_13", features);
    }

    public static OfMetadata mapMetadata(RoutingMetadata metadata) {
        return new OfMetadata(metadata.getValue(), metadata.getMask());
    }

    /**
     * Build switch properties object for tests.
     */
    public static SwitchProperties buildSwitchProperties(Switch sw, boolean multiTable) {
        return buildSwitchProperties(sw, multiTable, false, false);
    }

    /**
     * Build switch properties object for tests.
     */
    public static SwitchProperties buildSwitchProperties(
            Switch sw, boolean multiTable, boolean switchLldp, boolean switchArp) {
        return buildSwitchProperties(sw, multiTable, switchLldp, switchArp, false, RttState.DISABLED);
    }

    /**
     * Build switch properties object for tests.
     */
    public static SwitchProperties buildSwitchProperties(
            Switch sw, boolean multiTable, boolean switchLldp, boolean switchArp,
            boolean server42FlowRtt, RttState server42IslRtt) {
        return SwitchProperties.builder()
                .switchObj(sw)
                .multiTable(multiTable)
                .switchLldp(switchLldp)
                .switchArp(switchArp)
                .server42FlowRtt(server42FlowRtt)
                .server42IslRtt(server42IslRtt)
                .server42Port(SERVER_42_PORT)
                .server42Vlan(SERVER_42_VLAN)
                .server42MacAddress(SERVER_42_MAC_ADDRESS)
                .build();
    }

    /**
     * Find Speaker Command Data of specific type.
     */
    public static <C extends SpeakerData> C getCommand(Class<C> commandType,
                                                       List<SpeakerData> commands) {
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

    /**
     * Compare all fields of FieldMatch.
     */
    public static void assertEqualsMatch(Set<FieldMatch> expected, Set<FieldMatch> actual) {
        Set<FullComparableMatch> expectedSet = expected.stream().map(FullComparableMatch::new).collect(toSet());
        Set<FullComparableMatch> actualSet = actual.stream().map(FullComparableMatch::new).collect(toSet());
        Assert.assertEquals(expectedSet, actualSet);
    }

    private Utils() {
    }

    @Value
    private static class FullComparableMatch {
        long value;
        Long mask;
        Field field;

        public FullComparableMatch(FieldMatch match) {
            this.field = match.getField();
            this.value = match.getValue();
            this.mask = match.getMask();
        }
    }
}
