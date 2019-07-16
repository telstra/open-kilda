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
import static org.openkilda.floodlight.switchmanager.SwitchManager.DEFAULT_FLOW_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.EGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_COOKIE_MASK;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INTERNAL_ETH_DEST_OFFSET;
import static org.openkilda.floodlight.switchmanager.SwitchManager.MAC_ADDRESS_SIZE_IN_BITS;
import static org.openkilda.messaging.Utils.ETH_TYPE;

import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.model.FlowEncapsulationType;

import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.Arrays;
import java.util.List;

/**
 * Represent OF commands.
 * Code duplication is for more clear commands representation.
 */
public class PushSchemeOutputCommands implements OutputCommands {

    @Override
    public OFFlowAdd ingressMatchVlanIdFlowMod(int inputPort, int outputPort, int inputVlan,
                                               int tunnelId, long meterId, long cookie,
                                               FlowEncapsulationType encapsulationType,
                                               DatapathId ingressSwitchDpid) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, inputVlan, encapsulationType, ingressSwitchDpid))
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

    @Override
    public OFFlowAdd ingressNoMatchVlanIdFlowMod(int inputPort, int outputPort, int tunnelId,
                                                 long meterId, long cookie, FlowEncapsulationType encapsulationType,
                                                 DatapathId ingressSwitchDpId) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(INGRESS_TABLE_ID))
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(DEFAULT_FLOW_PRIORITY)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .build())
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions()
                                .applyActions(getPushActions(outputPort, tunnelId, encapsulationType,
                                        ingressSwitchDpId))
                                .createBuilder()
                                .build()))
                .setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS))
                .setXid(0L)
                .build();

    }

    @Override
    public OFFlowAdd egressPushFlowMod(int inputPort, int outputPort, int tunnelId, int outputVlan, long cookie,
                                       FlowEncapsulationType encapsulationType, DatapathId ingressSwitchDpid) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(EGRESS_TABLE_ID))
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, tunnelId, encapsulationType, ingressSwitchDpid))
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().popVlan(),
                                ofFactory.actions().buildPushVlan()
                                        .setEthertype(EthType.of(ETH_TYPE))
                                        .build(),
                                ofFactory.actions().buildSetField()
                                        .setField(ofFactory.oxms().buildVlanVid()
                                                .setValue(OFVlanVidMatch.ofVlan(outputVlan))
                                                .build())
                                        .build(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    private List<OFAction> getPushActions(int outputPort, int tunnelId, FlowEncapsulationType encapsulationType,
                                          DatapathId ingressSwitchDpId) {
        switch (encapsulationType) {
            default:
            case TRANSIT_VLAN:
                return Arrays.asList(
                        ofFactory.actions().buildPushVlan()
                                .setEthertype(EthType.of(ETH_TYPE))
                                .build(),
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

    protected List<OFAction> getPushVxlanAction(int outputPort, int tunnelId,
                                                DatapathId ingressSwitchDpId) {
        OFOxms oxms = ofFactory.oxms();

        return Arrays.asList(
                ofFactory.actions().buildNoviflowPushVxlanTunnel()
                        .setFlags((short) 0x01)
                        .setEthSrc(MacAddress.of(Arrays.copyOfRange(ingressSwitchDpId.getBytes(), 2, 8)))
                        .setEthDst(SwitchManager.STUB_VXLAN_ETH_DST_MAC)
                        .setIpv4Src(SwitchManager.STUB_VXLAN_IPV4_SRC)
                        .setIpv4Dst(SwitchManager.STUB_VXLAN_IPV4_DST)
                        .setUdpSrc(SwitchManager.STUB_VXLAN_UDP_SRC)
                        .setVni(tunnelId)
                        .build(),
                ofFactory.actions().buildNoviflowCopyField().setNBits(MAC_ADDRESS_SIZE_IN_BITS)
                        .setSrcOffset(INTERNAL_ETH_DEST_OFFSET)
                        .setDstOffset(0)
                        .setOxmSrcHeader(oxms.buildNoviflowPacketOffset().getTypeLen())
                        .setOxmDstHeader(oxms.buildNoviflowPacketOffset().getTypeLen()).build(),
                ofFactory.actions().buildOutput()
                        .setMaxLen(0xFFFFFFFF)
                        .setPort(OFPort.of(outputPort))
                        .build());
    }

    private List<OFAction> getPopActions(int outputPort, FlowEncapsulationType encapsulationType) {
        switch (encapsulationType) {
            default:
            case TRANSIT_VLAN:
                return Arrays.asList(
                        ofFactory.actions().popVlan(),
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
    public OFFlowAdd egressPopFlowMod(int inputPort, int outputPort, int tunnelId, long cookie,
                                      FlowEncapsulationType encapsulationType, DatapathId ingressSwitchDpId) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, tunnelId, encapsulationType, ingressSwitchDpId))
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(getPopActions(outputPort, encapsulationType))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    @Override
    public OFFlowAdd egressFlowMod(int inputPort, int outputPort, int tunnelId, long cookie,
                                   FlowEncapsulationType encapsulationType, OFInstructionApplyActions actions,
                                   DatapathId ingressSwitchDpid) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.EGRESS_TABLE_ID))
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, tunnelId, encapsulationType, ingressSwitchDpid))
                .setInstructions(singletonList(actions))
                .setXid(0L)
                .build();
    }

    @Override
    public OFFlowAdd egressNoneFlowMod(int inputPort, int outputPort, int tunnelId, long cookie,
                                       FlowEncapsulationType encapsulationType, DatapathId ingressSwitchDpId) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(EGRESS_TABLE_ID))
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, tunnelId, encapsulationType, ingressSwitchDpId))
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(getPopActions(outputPort, encapsulationType))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    @Override
    public OFFlowAdd egressReplaceFlowMod(int inputPort, int outputPort, int inputVlan, int outputVlan, long cookie,
                                          FlowEncapsulationType encapsulationType, DatapathId ingressSwitchDpId) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(EGRESS_TABLE_ID))
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(inputVlan))
                        .build())
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().popVlan(),
                                ofFactory.actions().buildSetField()
                                        .setField(ofFactory.oxms().buildVlanVid()
                                                .setValue(OFVlanVidMatch.ofVlan(outputVlan))
                                                .build())
                                        .build(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }
}
