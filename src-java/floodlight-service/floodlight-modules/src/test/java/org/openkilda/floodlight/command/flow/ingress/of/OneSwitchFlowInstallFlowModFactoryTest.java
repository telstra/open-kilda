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

import org.openkilda.floodlight.command.flow.ingress.OneSwitchFlowInstallCommand;
import org.openkilda.floodlight.model.EffectiveIds;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;

import org.junit.jupiter.api.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class OneSwitchFlowInstallFlowModFactoryTest extends IngressFlowModFactoryTest {
    private static final FlowEndpoint egressEndpointSingleVlan = new FlowEndpoint(
            new SwitchId(datapathIdAlpha.getLong()),
            endpointSingleVlan.getPortNumber() + 10, endpointSingleVlan.getOuterVlanId() + 10);
    private static final FlowEndpoint egressEndpointDoubleVlan = new FlowEndpoint(
            new SwitchId(datapathIdAlpha.getLong()),
            endpointDoubleVlan.getPortNumber() + 10,
            endpointDoubleVlan.getOuterVlanId() + 10, endpointDoubleVlan.getInnerVlanId() + 10);

    // --- makeDefaultPortForwardMessage

    @Test
    public void makeDefaultPortForwardMessageMessageMeteredZeroToSingle() {
        OneSwitchFlowInstallCommand command = makeCommand(endpointZeroVlan, egressEndpointSingleVlan, meterConfig);
        testMakeDefaultPortForwardMessageMessage(command);
    }

    @Test
    public void makeDefaultPortForwardMessageMessageMeteredZeroToDouble() {
        OneSwitchFlowInstallCommand command = makeCommand(endpointZeroVlan, egressEndpointDoubleVlan, meterConfig);
        testMakeDefaultPortForwardMessageMessage(command);
    }

    private void testMakeDefaultPortForwardMessageMessage(OneSwitchFlowInstallCommand command) {
        OFFlowAdd expected = makeForwardingMessage(
                command, -1,
                of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build(), getTargetIngressTableId(), Collections.emptyList());
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeDefaultPortForwardMessage(
                        new EffectiveIds(getEffectiveMeterId(command.getMeterConfig()), null)));
    }

    // --- makeSingleVlanForwardMessage
    @Test
    public void makeSingleVlanForwardMessageMeterless() {
        testMakeSingleVlanForwardMessage(null);
    }

    @Test
    public void makeSingleVlanForwardMessageMetered() {
        testMakeSingleVlanForwardMessage(meterConfig);
    }

    private void testMakeSingleVlanForwardMessage(MeterConfig meter) {
        OneSwitchFlowInstallCommand command = makeCommand(endpointSingleVlan, egressEndpointSingleVlan, meter);
        FlowEndpoint endpoint = command.getEndpoint();
        RoutingMetadata metadata = RoutingMetadata.builder()
                .outerVlanId(endpoint.getOuterVlanId())
                .build(Collections.emptySet());
        OFFlowAdd expected = makeForwardingMessage(
                command, 0,
                of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                        .setMasked(
                                MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build(), getTargetIngressTableId(), FlowEndpoint.makeVlanStack(endpoint.getInnerVlanId()));
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeSingleVlanForwardMessage(
                        new EffectiveIds(getEffectiveMeterId(command.getMeterConfig()), null)));
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
        OneSwitchFlowInstallCommand command = makeCommand(endpointDoubleVlan, egressEndpointDoubleVlan, meter);
        FlowEndpoint endpoint = command.getEndpoint();
        RoutingMetadata metadata = RoutingMetadata.builder()
                .outerVlanId(endpoint.getOuterVlanId())
                .build(Collections.emptySet());
        OFFlowAdd expected = makeForwardingMessage(
                command, 10,
                OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), endpoint.getInnerVlanId())
                        .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                        .setMasked(
                                MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build(), getTargetIngressTableId(), FlowEndpoint.makeVlanStack(endpoint.getInnerVlanId()));
        IngressFlowModFactory factory = makeFactory(command);
        verifyOfMessageEquals(
                expected, factory.makeDoubleVlanForwardMessage(
                        new EffectiveIds(getEffectiveMeterId(command.getMeterConfig()), null)));
    }

    // --- service methods

    private OFFlowAdd makeForwardingMessage(
            OneSwitchFlowInstallCommand command, int priorityOffset,
            Match match, TableId tableId, List<Integer> vlanStack) {
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        if (command.getMeterConfig() != null) {
            OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        }

        applyActions.addAll(OfAdapter.INSTANCE.makeVlanReplaceActions(
                of, vlanStack, command.getEgressEndpoint().getVlanStack()));

        FlowEndpoint ingress = command.getEndpoint();
        FlowEndpoint egress = command.getEgressEndpoint();
        OFPort outPort = ingress.getPortNumber().equals(egress.getPortNumber())
                ? OFPort.IN_PORT
                : OFPort.of(egress.getPortNumber());
        applyActions.add(of.actions().buildOutput().setPort(outPort).build());
        instructions.add(of.instructions().applyActions(applyActions));
        getGoToTableInstruction().ifPresent(instructions::add);
        getWriteMetadataInstruction().ifPresent(instructions::add);

        return of.buildFlowAdd()
                .setTableId(tableId)
                .setPriority(OneSwitchFlowInstallCommand.FLOW_PRIORITY + priorityOffset)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(match)
                .setInstructions(instructions)
                .build();
    }

    @Override
    IngressFlowModFactory makeFactory() {
        return makeFactory(makeCommand());
    }

    IngressFlowModFactory makeFactory(OneSwitchFlowInstallCommand command) {
        return new OneSwitchFlowInstallFlowModFactory(command, sw, switchFeatures);
    }

    OneSwitchFlowInstallCommand makeCommand() {
        return makeCommand(endpointSingleVlan, egressEndpointSingleVlan, meterConfig);
    }

    OneSwitchFlowInstallCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint egressEndpoint, MeterConfig meterConfig) {
        UUID commandId = UUID.randomUUID();
        return new OneSwitchFlowInstallCommand(
                new MessageContext(commandId.toString()), commandId, makeMetadata(), endpoint, meterConfig,
                egressEndpoint, RulesContext.builder().build(), null, new HashSet<>());
    }


    FlowSegmentMetadata makeMetadata() {
        return new FlowSegmentMetadata(flowId, cookie);
    }

    @Override
    TableId getTargetPreIngressTableId() {
        return TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID);
    }

    @Override
    TableId getTargetIngressTableId() {
        return TableId.of(SwitchManager.INGRESS_TABLE_ID);
    }

    Optional<OFInstructionGotoTable> getGoToTableInstruction() {
        return Optional.of(of.instructions().gotoTable(TableId.of(SwitchManager.POST_INGRESS_TABLE_ID)));
    }

    Optional<OFInstructionWriteMetadata> getWriteMetadataInstruction() {
        RoutingMetadata metadata = RoutingMetadata.builder().oneSwitchFlowFlag(true).build(switchFeatures);
        return Optional.of(of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()));
    }
}
