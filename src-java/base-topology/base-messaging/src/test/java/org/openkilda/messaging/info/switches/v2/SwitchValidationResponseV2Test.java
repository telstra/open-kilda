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

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SwitchValidationResponseV2Test {

    @Test
    public void actionSerializationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(GroupsValidationEntryV2.builder()
                        .asExpected(true)
                        .missing(Lists.newArrayList(
                                GroupInfoEntryV2.builder().groupId(2).flowId("123").build(),
                                GroupInfoEntryV2.builder().groupId(6).flowId("1233").build(),
                                GroupInfoEntryV2.builder().groupId(5).flowId("1243").build(),
                                GroupInfoEntryV2.builder().groupId(8).flowId("1263").build()
                                ))
                        .excess(new ArrayList<>())
                        .build())
                .meters(MetersValidationEntryV2.builder().build())
                .logicalPorts(LogicalPortsValidationEntryV2.builder().build())
                .rules(RulesValidationEntryV2.builder().build())
                .build();

        List<SwitchValidationResponseV2> list = response.split(4);
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        Assert.assertEquals(response, united);
    }
}
