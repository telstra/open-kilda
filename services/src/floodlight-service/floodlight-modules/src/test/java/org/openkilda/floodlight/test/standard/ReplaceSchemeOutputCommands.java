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
import static org.openkilda.floodlight.switchmanager.SwitchManager.EGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_COOKIE_MASK;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INGRESS_TABLE_ID;

import org.openkilda.messaging.Utils;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.OutputVlanType;

import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReplaceSchemeOutputCommands extends PushSchemeOutputCommands {
    @Override
    public OFFlowAdd ingressMatchVlanIdFlowMod(int inputPort, int outputPort, int inputVlan,
                                               int tunnelId, long meterId, long cookie,
                                               FlowEncapsulationType encapsulationType, DatapathId ingressSwitchDpid) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(INGRESS_TABLE_ID))
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, inputVlan, FlowEncapsulationType.TRANSIT_VLAN, ingressSwitchDpid))
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions()
                                .applyActions(getPushActions(outputPort, tunnelId, encapsulationType,
                                        ingressSwitchDpid))
                                .createBuilder()
                                .build()))
                .setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS))
                .setXid(0L)
                .build();

    }

    private OFFlowAdd egressPushFlowMod(int inputPort, int tunnelId, long cookie,
                                        FlowEncapsulationType encapsulationType, List<OFAction> actions,
                                        DatapathId ingressSwitchDpid) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(EGRESS_TABLE_ID))
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, tunnelId, encapsulationType, ingressSwitchDpid))
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(actions)
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    @Override
    public OFFlowAdd egressPushFlowMod(int inputPort, int outputPort, int tunnelId, int outputVlan, long cookie,
                                       FlowEncapsulationType encapsulationType,  DatapathId ingressSwitchDpid) {
        return egressPushFlowMod(inputPort, tunnelId, cookie, encapsulationType,
                getPopActions(OutputVlanType.PUSH, outputPort, outputVlan, encapsulationType), ingressSwitchDpid);
    }

    private List<OFAction> getPushActions(int outputPort, int tunnelId, FlowEncapsulationType encapsulationType,
                                          DatapathId ingressSwitchDpId) {
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
                return getPushVxlanAction(outputPort, tunnelId, ingressSwitchDpId);
        }
    }

    private List<OFAction> getPopActions(OutputVlanType outputVlanType,
                                         int outputPort, int outputVlan, FlowEncapsulationType encapsulationType) {
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
                List<OFAction> actions = new ArrayList<>();
                actions.add(ofFactory.actions().noviflowPopVxlanTunnel());
                actions.addAll(buildOutputVlanForVxlanActions(outputVlanType, outputVlan));
                actions.add(ofFactory.actions().buildOutput()
                        .setMaxLen(0xFFFFFFFF)
                        .setPort(OFPort.of(outputPort))
                        .build());
                return actions;
        }
    }

    private List<OFAction> buildOutputVlanForVxlanActions(OutputVlanType outputVlanType, int outputVlan) {

        if (outputVlanType == OutputVlanType.PUSH) {
            return Arrays.asList(ofFactory.actions().buildPushVlan().setEthertype(EthType.of(Utils.ETH_TYPE)).build(),
                    ofFactory.actions().buildSetField().setField(ofFactory.oxms()
                            .buildVlanVid()
                            .setValue(OFVlanVidMatch.ofVlan(outputVlan))
                            .build()).build());
        } else if (outputVlanType == OutputVlanType.REPLACE) {
            return Collections.singletonList(ofFactory.actions().buildSetField().setField(ofFactory.oxms()
                    .buildVlanVid()
                    .setValue(OFVlanVidMatch.ofVlan(outputVlan))
                    .build()).build());
        } else if (outputVlanType == OutputVlanType.POP) {
            return Collections.singletonList(ofFactory.actions().popVlan());
        }
        return null;
    }

    @Override
    public OFFlowAdd egressPopFlowMod(int inputPort, int outputPort, int transitVlan, long cookie,
                                      FlowEncapsulationType encapsulationType, DatapathId ingressSwitchDpid) {
        if (encapsulationType == FlowEncapsulationType.TRANSIT_VLAN) {
            return egressNoneFlowMod(inputPort, outputPort, transitVlan, cookie, encapsulationType, ingressSwitchDpid);
        } else if (encapsulationType == FlowEncapsulationType.VXLAN) {
            List<OFAction> actions = new ArrayList<>();
            actions.add(ofFactory.actions().noviflowPopVxlanTunnel());
            actions.addAll(buildOutputVlanForVxlanActions(OutputVlanType.POP, -1));
            actions.add(ofFactory.actions().buildOutput()
                    .setMaxLen(0xFFFFFFFF)
                    .setPort(OFPort.of(outputPort))
                    .build());
            return egressFlowMod(inputPort, outputPort, transitVlan, cookie, encapsulationType,
                    ofFactory.instructions().applyActions(actions)
                            .createBuilder()
                            .build(), ingressSwitchDpid);
        }
        return null;
    }

    @Override
    public OFFlowAdd egressReplaceFlowMod(int inputPort, int outputPort, int inputVlan, int outputVlan, long cookie,
                                          FlowEncapsulationType encapsulationType, DatapathId ingressSwitchDpid) {
        List<OFAction> actions = getPopActions(OutputVlanType.REPLACE, outputPort, outputVlan, encapsulationType);
        return egressPushFlowMod(inputPort, inputVlan, cookie, encapsulationType, actions, ingressSwitchDpid);
    }
}
