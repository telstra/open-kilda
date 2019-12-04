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

package org.openkilda.floodlight.command.flow.ingress.of;

import org.openkilda.floodlight.command.flow.FlowSegmentCommand;
import org.openkilda.floodlight.command.flow.ingress.IngressFlowSegmentInstallCommand;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.IngressSegmentCookie;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

abstract class IngressFlowSegmentInstallFlowModFactoryTest extends IngressFlowModFactoryTest {
    // --- makeOuterOnlyVlanForwardMessage

    @Test
    public void makeOuterOnlyVlanForwardMessageMeterlessVlanEncoded() {
        testMakeOuterOnlyVlanForwardMessageVlanEncoded(null);
    }

    @Test
    public void makeOuterOnlyVlanForwardMessageMeteredVlanEncoded() {
        testMakeOuterOnlyVlanForwardMessageVlanEncoded(meterConfig);
    }

    private void testMakeOuterOnlyVlanForwardMessageVlanEncoded(MeterConfig meter) {
        IngressFlowSegmentInstallCommand command = makeCommand(
                endpointSingleVlan, meter, encapsulationVlan);
        FlowEndpoint endpoint = command.getEndpoint();

        List<OFAction> vlanTransformation = OfAdapter.INSTANCE.makeVlanReplaceActions(
                of, endpoint.getVlanStack(), makeTransitVlanStack(endpoint, command.getEncapsulation().getId()));
        OFFlowAdd expected = makeVlanForwardingMessage(
                command, 0, IngressSegmentCookie.IngressSegmentSubType.OUTER_VLAN_ONLY,
                OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getOuterVlanId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build(), getTargetIngressTableId(), vlanTransformation);
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeOuterOnlyVlanForwardMessage(getEffectiveMeterId(command.getMeterConfig())));
    }

    @Test
    public void makeOuterOnlyVlanForwardMessageMeterlessVxLanEncoded() {
        testMakeOuterOnlyVlanForwardMessageVxLanEncoded(null);
    }

    @Test
    public void makeOuterOnlyVlanForwardMessageMeteredVxLanEncoded() {
        testMakeOuterOnlyVlanForwardMessageVxLanEncoded(meterConfig);
    }

    private void testMakeOuterOnlyVlanForwardMessageVxLanEncoded(MeterConfig meter) {
        IngressFlowSegmentInstallCommand command = makeCommand(
                endpointSingleVlan, meter, encapsulationVxLan);

        OFFlowAdd expected = makeVxLanForwardingMessage(
                command, 0, IngressSegmentCookie.IngressSegmentSubType.OUTER_VLAN_ONLY,
                OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getOuterVlanId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build(), getTargetIngressTableId(), Collections.emptyList());
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeOuterOnlyVlanForwardMessage(getEffectiveMeterId(command.getMeterConfig())));
    }

    @Test
    public void makeOuterOnlyVlanForwardMessageConnectedDevices() {
        FlowEndpoint endpoint = new FlowEndpoint(
                endpointSingleVlan.getSwitchId(), endpointSingleVlan.getPortNumber(),
                endpointSingleVlan.getOuterVlanId(), 0, true);
        IngressFlowSegmentInstallCommand command = makeCommand(endpoint, meterConfig, encapsulationVlan);

        IngressFlowModFactory factory = makeFactory(command);
        verifyGoToTableInstruction(factory.makeOuterOnlyVlanForwardMessage(meterConfig.getId()), TableId.of(
                SwitchManager.POST_INGRESS_TABLE_ID));
    }

    // --- makeSingleVlanForwardMessage

    @Test
    public void makeSingleVlanForwardMessageMeterlessVlanEncoded() {
        testMakeSingleVlanForwardMessageVlanEncoded(null);
    }

    @Test
    public void makeSingleVlanForwardMessageMeteredVlanEncoded() {
        testMakeSingleVlanForwardMessageVlanEncoded(meterConfig);
    }

    private void testMakeSingleVlanForwardMessageVlanEncoded(MeterConfig meter) {
        IngressFlowSegmentInstallCommand command = makeCommand(
                endpointSingleVlan, meter, encapsulationVlan);

        FlowEndpoint endpoint = command.getEndpoint();
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        List<OFAction> vlanTransformation = OfAdapter.INSTANCE.makeVlanReplaceActions(
                of, Collections.emptyList(), Collections.singletonList(command.getEncapsulation().getId()));
        OFFlowAdd expected = makeVlanForwardingMessage(
                command, -10, IngressSegmentCookie.IngressSegmentSubType.SINGLE_VLAN_FORWARD,
                of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                        .setMasked(
                                MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build(), getTargetIngressTableId(), vlanTransformation);
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeSingleVlanForwardMessage(getEffectiveMeterId(command.getMeterConfig())));
    }

    @Test
    public void makeSingleVlanForwardMessageMeterlessVxLanEncoded() {
        testMakeSingleVlanForwardMessageVxLanEncoded(meterConfig);
    }

    @Test
    public void makeSingleVlanForwardMessageMeteredVxLanEncoded() {
        testMakeSingleVlanForwardMessageVxLanEncoded(null);
    }

    private void testMakeSingleVlanForwardMessageVxLanEncoded(MeterConfig meter) {
        IngressFlowSegmentInstallCommand command = makeCommand(
                endpointSingleVlan, meter, encapsulationVxLan);

        FlowEndpoint endpoint = command.getEndpoint();
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        List<OFAction> vlanTransformation = OfAdapter.INSTANCE.makeVlanReplaceActions(
                of, Collections.emptyList(), Collections.singletonList(endpoint.getOuterVlanId()));
        OFFlowAdd expected = makeVxLanForwardingMessage(
                command, -10, IngressSegmentCookie.IngressSegmentSubType.SINGLE_VLAN_FORWARD,
                of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                        .setMasked(
                                MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build(), getTargetIngressTableId(), vlanTransformation);

        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeSingleVlanForwardMessage(getEffectiveMeterId(command.getMeterConfig())));
    }

    // --- makeDoubleVlanForwardMessage

    @Test
    public void makeDoubleVlanForwardMessageMeterlessVlanEncoded() {
        testMakeDoubleVlanForwardMessageVlanEncoded(null);
    }

    @Test
    public void makeDoubleVlanForwardMessageMeteredVlanEncoded() {
        testMakeDoubleVlanForwardMessageVlanEncoded(meterConfig);
    }

    private void testMakeDoubleVlanForwardMessageVlanEncoded(MeterConfig meter) {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointDoubleVlan, meter, encapsulationVlan);

        FlowEndpoint endpoint = command.getEndpoint();
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        List<OFAction> vlanTransformation = OfAdapter.INSTANCE.makeVlanReplaceActions(
                of,
                FlowEndpoint.makeVlanStack(endpoint.getInnerVlanId()),
                FlowEndpoint.makeVlanStack(command.getEncapsulation().getId()));
        OFFlowAdd expected = makeVlanForwardingMessage(
                command, 0, IngressSegmentCookie.IngressSegmentSubType.DOUBLE_VLAN_FORWARD,
                OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), endpoint.getInnerVlanId())
                        .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                        .setMasked(
                                MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build(), getTargetIngressTableId(), vlanTransformation);
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeDoubleVlanForwardMessage(getEffectiveMeterId(command.getMeterConfig())));
    }

    @Test
    public void makeDoubleVlanForwardMessageMeterlessVxLanEncoded() {
        testMakeDoubleVlanForwardMessageVxLanEncoded(null);
    }

    @Test
    public void makeDoubleVlanForwardMessageMeteredVxLanEncoded() {
        testMakeDoubleVlanForwardMessageVxLanEncoded(meterConfig);
    }

    private void testMakeDoubleVlanForwardMessageVxLanEncoded(MeterConfig meter) {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointDoubleVlan, meter, encapsulationVxLan);

        FlowEndpoint endpoint = command.getEndpoint();
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        List<OFAction> vlanTransformation = OfAdapter.INSTANCE.makeVlanReplaceActions(
                of,
                FlowEndpoint.makeVlanStack(endpoint.getInnerVlanId()),
                endpoint.getVlanStack());
        OFFlowAdd expected = makeVxLanForwardingMessage(
                command, 0, IngressSegmentCookie.IngressSegmentSubType.DOUBLE_VLAN_FORWARD,
                OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), endpoint.getInnerVlanId())
                        .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                        .setMasked(
                                MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build(), getTargetIngressTableId(), vlanTransformation);
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeDoubleVlanForwardMessage(getEffectiveMeterId(command.getMeterConfig())));
    }

    // --- makeDefaultPortForwardMessage

    @Test
    public void makeDefaultPortForwardMessageMeterlessVlanEncoded() {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointZeroVlan, null, encapsulationVlan);
        testMakeDefaultPortForwardMessageVlan(command);
    }

    @Test
    public void makeDefaultPortForwardMessageMeteredVlanEncoded() {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointZeroVlan, meterConfig, encapsulationVlan);
        testMakeDefaultPortForwardMessageVlan(command);
    }

    @Test
    public void makeDefaultPortForwardMessageMeteredVxLanEncoded() {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointZeroVlan, meterConfig, encapsulationVxLan);
        testMakeDefaultPortForwardMessageVxLan(command);
    }

    @Test
    public void makeDefaultPortForwardMessageConnectedDevices() {
        FlowEndpoint endpoint = new FlowEndpoint(
                endpointSingleVlan.getSwitchId(), endpointSingleVlan.getPortNumber(),
                endpointSingleVlan.getOuterVlanId(), 0, true);
        IngressFlowSegmentInstallCommand command = makeCommand(endpoint, meterConfig, encapsulationVlan);

        IngressFlowModFactory factory = makeFactory(command);
        verifyGoToTableInstruction(factory.makeDefaultPortForwardMessage(meterConfig.getId()), TableId.of(
                SwitchManager.POST_INGRESS_TABLE_ID));
    }

    private void testMakeDefaultPortForwardMessageVlan(IngressFlowSegmentInstallCommand command) {
        List<OFAction> vlanTransformation = OfAdapter.INSTANCE.makeVlanReplaceActions(
                of, Collections.emptyList(), Collections.singletonList(command.getEncapsulation().getId()));
        OFFlowAdd expected = makeVlanForwardingMessage(
                command, -1, IngressSegmentCookie.IngressSegmentSubType.DEFAULT_PORT_FORWARD,
                of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build(), getTargetPreIngressTableId(), vlanTransformation);
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(expected, factory.makeDefaultPortForwardMessage(getEffectiveMeterId(
                command.getMeterConfig())));
    }

    private void testMakeDefaultPortForwardMessageVxLan(IngressFlowSegmentInstallCommand command) {
        OFFlowAdd expected = makeVxLanForwardingMessage(
                command, -1, IngressSegmentCookie.IngressSegmentSubType.DEFAULT_PORT_FORWARD,
                of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build(), getTargetPreIngressTableId(), Collections.emptyList());
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(expected, factory.makeDefaultPortForwardMessage(
                getEffectiveMeterId(command.getMeterConfig())));
    }

    // --- service methods

    private OFFlowAdd makeVlanForwardingMessage(
            IngressFlowSegmentInstallCommand command, int priorityOffset,
            IngressSegmentCookie.IngressSegmentSubType subType, Match match, TableId tableId,
            List<OFAction> vlanTransformation) {
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        if (command.getMeterConfig() != null) {
            OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        }
        instructions.add(of.instructions().applyActions(applyActions));
        applyActions.addAll(vlanTransformation);
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());

        Cookie cookie = IngressSegmentCookie.convert(command.getCookie())
                .setForwarding(true)
                .setSubType(subType);
        return of.buildFlowAdd()
                .setTableId(tableId)
                .setPriority(FlowSegmentCommand.FLOW_PRIORITY + priorityOffset)
                .setCookie(U64.of(cookie.getValue()))
                .setMatch(match)
                .setInstructions(instructions)
                .build();
    }

    private OFFlowAdd makeVxLanForwardingMessage(
            IngressFlowSegmentInstallCommand command, int priorityOffset,
            IngressSegmentCookie.IngressSegmentSubType subType, Match match, TableId tableId,
            List<OFAction> vlanTransformation) {
        List<OFInstruction> instructions = new ArrayList<>();
        List<OFAction> applyActions = new ArrayList<>(vlanTransformation);
        if (command.getMeterConfig() != null) {
            OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        }
        applyActions.add(of.actions().buildNoviflowPushVxlanTunnel()
                .setEthSrc(MacAddress.of(DatapathId.of(command.getSwitchId().getId())))
                .setEthDst(MacAddress.of(command.getEgressSwitchId().toLong()))
                .setIpv4Src(IPv4Address.of("127.0.0.1"))
                .setIpv4Dst(IPv4Address.of("127.0.0.2"))
                .setUdpSrc(4500)
                .setVni(command.getEncapsulation().getId())
                .setFlags((short) 0x01)
                .build());
        applyActions.add(of.actions().buildNoviflowCopyField()
                .setOxmSrcHeader(of.oxms().buildNoviflowPacketOffset().getTypeLen())
                .setSrcOffset(56 * 8)
                .setOxmDstHeader(of.oxms().buildNoviflowPacketOffset().getTypeLen())
                .setDstOffset(6 * 8)
                .setNBits(MacAddress.BROADCAST.getLength() * 8)
                .build());
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        Cookie cookie = IngressSegmentCookie.convert(command.getCookie())
                .setForwarding(true)
                .setSubType(subType);
        return of.buildFlowAdd()
                .setTableId(tableId)
                .setPriority(FlowSegmentCommand.FLOW_PRIORITY + priorityOffset)
                .setCookie(U64.of(cookie.getValue()))
                .setMatch(match)
                .setInstructions(instructions)
                .build();
    }

    @Override
    IngressFlowModFactory makeFactory() {
        return makeFactory(makeCommand());
    }

    abstract IngressFlowModFactory makeFactory(IngressFlowSegmentInstallCommand command);

    private IngressFlowSegmentInstallCommand makeCommand() {
        return makeCommand(endpointSingleVlan, meterConfig, encapsulationVlan);
    }

    private IngressFlowSegmentInstallCommand makeCommand(
            FlowEndpoint endpoint, MeterConfig meterConfig, FlowTransitEncapsulation encapsulation) {
        UUID commandId = UUID.randomUUID();
        return new IngressFlowSegmentInstallCommand(
                new MessageContext(commandId.toString()), commandId, makeMetadata(), endpoint, meterConfig,
                new SwitchId(datapathIdBeta.getLong()), 1, encapsulation);
    }

    private List<Integer> makeTransitVlanStack(FlowEndpoint ingressEndpoint, int transitVlanId) {
        List<Integer> vlanStack = ingressEndpoint.getVlanStack();
        if (vlanStack.isEmpty()) {
            vlanStack.add(transitVlanId);
        } else {
            vlanStack.set(0, transitVlanId);
        }
        return vlanStack;
    }

    abstract FlowSegmentMetadata makeMetadata();
}
