/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class InstallOneSwitchRuleCommandTest extends FlowCommandTest {

    @Test
    public void samePort() {
        int inOutPort = 10;
        int inVlan = 1001;
        int outVlan = 1002;
        int meterId = 12;
        int cookie = 22;

        InstallOneSwitchRuleCommand command = new InstallOneSwitchRuleCommand(
                UUID.randomUUID(), FLOW_ID, new MessageContext(), new Cookie(cookie),
                SWITCH_ID,
                inOutPort, inOutPort,
                10L,
                inVlan,
                OutputVlanType.REPLACE,
                new MeterId(meterId),
                outVlan,
                false);

        OFFlowMod result = command.getInstallRuleCommand(iofSwitch, featureDetectorService);
        OFFactory of = iofSwitch.getOFFactory();
        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(SwitchManager.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inOutPort))
                        .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(inVlan))
                        .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().meter(meterId),
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().setField(of.oxms().vlanVid(OFVlanVidMatch.ofVlan(outVlan))),
                                of.actions().buildOutput().setPort(OFPort.IN_PORT).setMaxLen(0xFFFFFFFF).build()))))
                .build();
        Assert.assertEquals(expected, result);
    }
}
