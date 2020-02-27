/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import org.junit.Test;

public class CommandBuilderImplTest {

    private static final SwitchId SWITCH_ID_A = new SwitchId("00:10");

    private static CommandBuilderImpl commandBuilder = new CommandBuilderImpl();

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithTransitVlanEncapsulation() {
        Long cookie = Cookie.buildForwardCookie(1).getValue();
        String inPort = "1";
        String inVlan = "10";
        String outPort = "2";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, inVlan, outPort, null, false);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertEquals(Integer.valueOf(inVlan), criteria.getEncapsulationId());
        assertEquals(Integer.valueOf(outPort), criteria.getOutPort());
    }

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithStringOutPort() {
        Long cookie = Cookie.buildForwardCookie(1).getValue();
        String inPort = "1";
        String inVlan = "10";
        String outPort = "in_port";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, inVlan, outPort, null, false);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertEquals(Integer.valueOf(inVlan), criteria.getEncapsulationId());
        assertNull(criteria.getOutPort());
    }

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithVxlanEncapsulationIngress() {
        Long cookie = Cookie.buildForwardCookie(1).getValue();
        String inPort = "1";
        String outPort = "2";
        String tunnelId = "10";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, null, outPort, tunnelId, true);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertEquals(Integer.valueOf(tunnelId), criteria.getEncapsulationId());
        assertEquals(Integer.valueOf(outPort), criteria.getOutPort());
    }

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithVxlanEncapsulationTransitAndEgress() {
        Long cookie = Cookie.buildForwardCookie(1).getValue();
        String inPort = "1";
        String outPort = "2";
        String tunnelId = "10";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, null, outPort, tunnelId, false);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertEquals(Integer.valueOf(tunnelId), criteria.getEncapsulationId());
        assertEquals(Integer.valueOf(outPort), criteria.getOutPort());
    }

    private FlowEntry buildFlowEntry(Long cookie, String inPort, String inVlan, String outPort,
                                     String tunnelId, boolean tunnelIdIngressRule) {
        return FlowEntry.builder()
                .cookie(cookie)
                .match(FlowMatchField.builder()
                        .inPort(inPort)
                        .vlanVid(inVlan)
                        .tunnelId(!tunnelIdIngressRule ? tunnelId : null)
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .flowOutput(outPort)
                                .pushVxlan(tunnelIdIngressRule ? tunnelId : null)
                                .build())
                        .build())
                .build();
    }
}
