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

package org.openkilda.wfm.topology.switchmanager.service.impl.comparator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.OfTable;

import org.junit.Test;

public class RuleComparatorTest {

    @Test
    public void testEqual() {
        FlowEntry flowEntry = FlowEntry.builder()
                                .cookie(1L)
                                .tableId(1)
                                .build();
        FlowSpeakerCommandData flowSpeakerCommandData = FlowSpeakerCommandData.builder()
                .cookie(new Cookie(1L))
                .table(OfTable.PRE_INGRESS)
                .build();

        assertTrue(RuleComparator.compareRules(flowEntry, flowSpeakerCommandData));
    }

    @Test
    public void testNotEqual() {
        FlowEntry flowEntry = FlowEntry.builder()
                                .cookie(1L)
                                .tableId(1)
                                .priority(1)
                                .build();
        FlowSpeakerCommandData flowSpeakerCommandData = FlowSpeakerCommandData.builder()
                .cookie(new Cookie(1L))
                .table(OfTable.PRE_INGRESS)
                .priority(2)
                .build();

        assertFalse(RuleComparator.compareRules(flowEntry, flowSpeakerCommandData));
    }
}
