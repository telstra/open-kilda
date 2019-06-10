/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.test.standard;

import static java.util.Collections.singletonList;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_COOKIE_MASK;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_PRIORITY;

import org.openkilda.model.FlowEncapsulationType;

import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.Arrays;
import java.util.List;

public class ReplaceSchemeOutputCommands extends PushSchemeOutputCommands {
    @Override
    public OFFlowAdd ingressMatchVlanIdFlowMod(int inputPort, int outputPort, int inputVlan,
                                               int tunnelId, long meterId, long cookie,
                                               FlowEncapsulationType encapsulationType) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, inputVlan, encapsulationType))
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions()
                                .applyActions(getPushActions(outputPort, tunnelId, encapsulationType))
                                .createBuilder()
                                .build()))
                .setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS))
                .setXid(0L)
                .build();

    }

    private OFFlowAdd egressPushFlowMod(int inputPort, int tunnelId, long cookie,
                                        FlowEncapsulationType encapsulationType, List<OFAction> actions) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, tunnelId, encapsulationType))
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(actions)
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    @Override
    public OFFlowAdd egressPushFlowMod(int inputPort, int outputPort, int tunnelId, int outputVlan, long cookie,
                                       FlowEncapsulationType encapsulationType) {
        return egressPushFlowMod(inputPort, tunnelId, cookie, encapsulationType,
                getPopActions(outputPort, outputVlan, encapsulationType));
    }

    private List<OFAction> getPushActions(int outputPort, int tunnelId, FlowEncapsulationType encapsulationType) {
        switch (encapsulationType) {
            default:
            case TRANSIT_VLAN:
                return Arrays.asList(
                        ofFactory.actions().buildSetField()
                                .setField(ofFactory.oxms().buildVlanVid()
                                        .setValue(OFVlanVidMatch.ofVlan(tunnelId))
                                        .build())
                                .build(),
                        ofFactory.actions().buildOutput()
                                .setMaxLen(0xFFFFFFFF)
                                .setPort(OFPort.of(outputPort))
                                .build());
            case VXLAN:
                return getPushVxlanAction(outputPort, tunnelId);
        }
    }

    private List<OFAction> getPopActions(int outputPort, int outputVlan, FlowEncapsulationType encapsulationType) {
        switch (encapsulationType) {
            default:
            case TRANSIT_VLAN:
                return Arrays.asList(
                        ofFactory.actions().buildSetField()
                                .setField(ofFactory.oxms().buildVlanVid()
                                        .setValue(OFVlanVidMatch.ofVlan(outputVlan))
                                        .build())
                                .build(),
                        ofFactory.actions().buildOutput()
                                .setMaxLen(0xFFFFFFFF)
                                .setPort(OFPort.of(outputPort))
                                .build());
            case VXLAN:
                return Arrays.asList(
                        ofFactory.actions().noviflowPopVxlanTunnel(),
                        ofFactory.actions().buildOutput()
                                .setMaxLen(0xFFFFFFFF)
                                .setPort(OFPort.of(outputPort))
                                .build());
        }
    }

    @Override
    public OFFlowAdd egressPopFlowMod(int inputPort, int outputPort, int transitVlan, long cookie,
                                      FlowEncapsulationType encapsulationType) {
        return egressNoneFlowMod(inputPort, outputPort, transitVlan, cookie, encapsulationType);
    }

    @Override
    public OFFlowAdd egressReplaceFlowMod(int inputPort, int outputPort, int inputVlan, int outputVlan, long cookie,
                                          FlowEncapsulationType encapsulationType) {
        List<OFAction> actions = getPopActions(outputPort, outputVlan, encapsulationType);
        if (encapsulationType == FlowEncapsulationType.VXLAN) {
            actions = Arrays.asList(
                    ofFactory.actions().noviflowPopVxlanTunnel(),
                    ofFactory.actions().buildSetField().setField(ofFactory.oxms().buildVlanVid()
                            .setValue(OFVlanVidMatch.ofVlan(outputVlan))
                            .build()).build(),
                    ofFactory.actions().buildOutput()
                            .setMaxLen(0xFFFFFFFF)
                            .setPort(OFPort.of(outputPort))
                            .build());
        }
        return egressPushFlowMod(inputPort, inputVlan, cookie, encapsulationType, actions);
    }
}
