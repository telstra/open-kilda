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

package org.openkilda.messaging.info.switches.v2;

import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.Instructions;
import org.openkilda.messaging.info.switches.v2.action.CopyFieldActionEntry;
import org.openkilda.messaging.info.switches.v2.action.GroupActionEntry;
import org.openkilda.messaging.info.switches.v2.action.MeterActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PopVlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PopVxlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PortOutActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PushVlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PushVxlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.SetFieldActionEntry;
import org.openkilda.messaging.info.switches.v2.action.SwapFieldActionEntry;
import org.openkilda.model.MeterId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

public class RuleInfoEntryV2Test {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void actionSerializationTest() throws Exception {

        RuleInfoEntryV2 origin = RuleInfoEntryV2.builder()
                .instructions(Instructions.builder()
                        .applyActions(Lists.newArrayList(
                                CopyFieldActionEntry.builder().dstHeader("abc").dstOffset(1).srcHeader("bgf")
                                        .srcOffset(2).numberOfBits(15).build(),
                                GroupActionEntry.builder().groupId(2L).build(),
                                MeterActionEntry.builder().meterId(new MeterId(7)).build(),
                                PopVlanActionEntry.builder().build(),
                                PopVxlanActionEntry.builder().actionType("ovs").build(),
                                PortOutActionEntry.builder().portNumber(5).portType("ALL").build(),
                                PushVlanActionEntry.builder().build(),
                                PushVxlanActionEntry.builder().actionType("some2").vni(1).udpSrc(15).build(),
                                SetFieldActionEntry.builder().field("some").value(15).build(),
                                SwapFieldActionEntry.builder().actionType("ovs").srcHeader("qwe").srcOffset(8)
                                        .dstHeader("rty").dstOffset(9).numberOfBits(19).build()))
                        .build())
                .build();
        String json = mapper.writeValueAsString(origin);

        RuleInfoEntryV2 reconstructed = mapper.readValue(json, RuleInfoEntryV2.class);
        Assert.assertEquals(origin, reconstructed);
    }
}
