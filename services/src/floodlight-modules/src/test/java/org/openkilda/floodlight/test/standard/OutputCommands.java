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
import static org.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.openkilda.floodlight.switchmanager.SwitchManager.DEFAULT_RULE_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_COOKIE_MASK;
import static org.openkilda.messaging.Utils.ETH_TYPE;
import static org.projectfloodlight.openflow.protocol.OFMeterFlags.BURST;
import static org.projectfloodlight.openflow.protocol.OFMeterFlags.KBPS;
import static org.projectfloodlight.openflow.protocol.OFMeterModCommand.ADD;

import org.openkilda.floodlight.OFFactoryMock;
import org.openkilda.floodlight.switchmanager.SwitchManager;

import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.Arrays;
import java.util.HashSet;

public interface OutputCommands {
    OFFactory ofFactory = new OFFactoryMock();

    OFFlowAdd egressReplaceFlowMod(int inputPort, int outputPort, int inputVlan, int outputVlan, long cookie);

    OFFlowAdd egressPopFlowMod(int inputPort, int outputPort, int transitVlanId, long cookie);

    OFFlowAdd egressPushFlowMod(int inputPort, int outputPort, int transitVlanId, int outputVlan, long cookie);

    OFFlowAdd egressNoneFlowMod(int inputPort, int outputPort, int transitVlanId, long cookie);

    OFFlowAdd ingressMatchVlanIdFlowMod(int inputPort, int outputPort, int inputVlan, int transitVlan,
                                        long meterId, long cookie);

    OFFlowAdd ingressNoMatchVlanIdFlowMod(int inputPort, int outputPort, int transitVlan,
                                          long meterId, long cookie);

    default OFFlowAdd ingressReplaceFlowMod(int inputPort, int outputPort, int inputVlan, int transitVlan,
                                            long meterId, long cookie) {
        return ingressMatchVlanIdFlowMod(inputPort, outputPort, inputVlan, transitVlan, meterId, cookie);
    }

    default OFFlowAdd ingressNoneFlowMod(int inputPort, int outputPort, int transitVlan, long meterId, long cookie) {
        return ingressNoMatchVlanIdFlowMod(inputPort, outputPort, transitVlan, meterId, cookie);
    }

    default OFFlowAdd ingressPushFlowMod(int inputPort, int outputPort, int transitVlan, long meterId, long cookie) {
        return ingressNoMatchVlanIdFlowMod(inputPort, outputPort, transitVlan, meterId, cookie);
    }

    default OFFlowAdd ingressPopFlowMod(int inputPort, int outputPort, int inputVlan, int transitVlan,
                                        long meterId, long cookie) {
        return ingressMatchVlanIdFlowMod(inputPort, outputPort, inputVlan, transitVlan, meterId, cookie);
    }

    default OFFlowAdd transitFlowMod(int inputPort, int outputPort, int transitVlan, long cookie) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(DEFAULT_RULE_PRIORITY)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(transitVlan))
                        .build())
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(singletonList(
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    default OFFlowAdd oneSwitchReplaceFlowMod(int inputPort, int outputPort, int inputVlan, int outputVlan,
                                              long meterId, long cookie) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(DEFAULT_RULE_PRIORITY)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(inputVlan))
                        .build())
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions().applyActions(Arrays.asList(
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

    default OFFlowAdd oneSwitchNoneFlowMod(int inputPort, int outputPort, long meterId, long cookie) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(DEFAULT_RULE_PRIORITY)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .build())
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions().applyActions(singletonList(
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    default OFFlowAdd oneSwitchPopFlowMod(int inputPort, int outputPort, int inputVlan, long meterId, long cookie) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(DEFAULT_RULE_PRIORITY)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(inputVlan))
                        .build())
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().popVlan(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    default OFFlowAdd oneSwitchPushFlowMod(int inputPort, int outputPort, int outputVlan, long meterId, long cookie) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(DEFAULT_RULE_PRIORITY)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .build())
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions().applyActions(Arrays.asList(
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

    default OFMeterMod installMeter(long bandwidth, long burstSize, long meterId) {
        return ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(ADD)
                .setMeters(singletonList(ofFactory.meterBands()
                        .buildDrop()
                        .setRate(bandwidth)
                        .setBurstSize(burstSize).build()))
                .setFlags(new HashSet<>(Arrays.asList(KBPS, BURST)))
                .setXid(0L)
                .build();
    }

    default OFFlowAdd installDropLoopRule(DatapathId dpid) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, MacAddress.of(VERIFICATION_BCAST_PACKET_DST))
                .setExact(MatchField.ETH_SRC, MacAddress.of(Arrays.copyOfRange(dpid.getBytes(), 2, 8)))
                .build();

        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(SwitchManager.DROP_VERIFICATION_LOOP_RULE_COOKIE))
                .setPriority(SwitchManager.DROP_VERIFICATION_LOOP_RULE_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .build();
    }
}
