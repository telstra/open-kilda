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

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;

import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.Test;

public class FlowSpeakerCommandDataTest {
    @Test
    public void flowCommandWithDifferentMatchValues() {
        FlowSpeakerData command = FlowSpeakerData.builder()
                .match(newHashSet(
                        FieldMatch.builder().field(Field.VLAN_VID).value(1).build(),
                        FieldMatch.builder().field(Field.VLAN_VID).value(2).build()))
                .build();
        assertEquals(1, command.getMatch().size());
    }

    @Test
    public void flowCommandWithEqualMatchValues() {
        FlowSpeakerData command = FlowSpeakerData.builder()
                .match(newHashSet(
                        FieldMatch.builder().field(Field.VLAN_VID).value(1).build(),
                        FieldMatch.builder().field(Field.VLAN_VID).value(1).build()))
                .build();
        assertEquals(1, command.getMatch().size());
    }

    @Test
    public void flowCommandWithDifferentMatchFields() {
        FlowSpeakerData command = FlowSpeakerData.builder()
                .match(newHashSet(
                        FieldMatch.builder().field(Field.VLAN_VID).value(1).build(),
                        FieldMatch.builder().field(Field.IN_PORT).value(1).build()))
                .build();
        assertEquals(2, command.getMatch().size());
    }
}
