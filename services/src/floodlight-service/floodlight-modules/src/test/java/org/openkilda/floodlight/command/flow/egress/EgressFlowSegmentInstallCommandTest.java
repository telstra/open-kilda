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

package org.openkilda.floodlight.command.flow.egress;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class EgressFlowSegmentInstallCommandTest extends EgressFlowSegmentBlankCommandTest {
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void happyPathTransitVlanZeroVlanToZeroVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressDefaultPort, endpointIngresDefaultPort, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().popVlan(),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanZeroVlanToSingleVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressSingleVlan, endpointIngresDefaultPort, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanSingleVlanToZeroVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressDefaultPort, endpointIngressSingleVlan, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().popVlan(),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanSingleVlanToSingleVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressSingleVlan, endpointIngressSingleVlan, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                         .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                         .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVxLanZeroVlanToZeroVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressDefaultPort, endpointIngresDefaultPort, encapsulationVxLan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVxLAnVni(of, of.buildMatch(), command.getEncapsulation().getId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                        .setExact(MatchField.ETH_DST, MacAddress.of(command.getSwitchId().toLong()))
                        .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                        .setExact(MatchField.UDP_DST, TransportPort.of(4789))
                        .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().noviflowPopVxlanTunnel(),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVxLanZeroVlanToSingleVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressSingleVlan, endpointIngresDefaultPort, encapsulationVxLan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVxLAnVni(of, of.buildMatch(), command.getEncapsulation().getId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                        .setExact(MatchField.ETH_DST, MacAddress.of(command.getSwitchId().toLong()))
                        .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                        .setExact(MatchField.UDP_DST, TransportPort.of(4789))
                        .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().noviflowPopVxlanTunnel(),
                                of.actions().pushVlan(EthType.VLAN_FRAME),
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanSingleVxLanToZeroVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressDefaultPort, endpointIngressSingleVlan, encapsulationVxLan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVxLAnVni(of, of.buildMatch(), command.getEncapsulation().getId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                        .setExact(MatchField.ETH_DST, MacAddress.of(command.getSwitchId().toLong()))
                        .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                        .setExact(MatchField.UDP_DST, TransportPort.of(4789))
                        .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().noviflowPopVxlanTunnel(),
                                of.actions().popVlan(),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanSingleVxLanToSingleVlan() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressSingleVlan, endpointIngressSingleVlan, encapsulationVxLan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVxLAnVni(of, of.buildMatch(), command.getEncapsulation().getId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                        .setExact(MatchField.ETH_DST, MacAddress.of(command.getSwitchId().toLong()))
                        .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                        .setExact(MatchField.UDP_DST, TransportPort.of(4789))
                        .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().noviflowPopVxlanTunnel(),
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanMultiTable() throws Exception {
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointEgressSingleVlan, endpointIngressSingleVlan, encapsulationVlan,
                new FlowSegmentMetadata("egress-flow-segment-multitable", new Cookie(3), true));
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.EGRESS_TABLE_ID))
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                         .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                         .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Override
    protected EgressFlowSegmentInstallCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation) {
        FlowSegmentMetadata metadata = new FlowSegmentMetadata(
                "egress-flow-segment-install-flow-id", new Cookie(3), false);
        return makeCommand(endpoint, ingressEndpoint, encapsulation, metadata);
    }

    protected EgressFlowSegmentInstallCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation,
            FlowSegmentMetadata metadata) {
        MessageContext messageContext = new MessageContext();
        UUID commandId = UUID.randomUUID();
        int islPort = 6;
        return new EgressFlowSegmentInstallCommand(
                messageContext, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation);
    }
}
