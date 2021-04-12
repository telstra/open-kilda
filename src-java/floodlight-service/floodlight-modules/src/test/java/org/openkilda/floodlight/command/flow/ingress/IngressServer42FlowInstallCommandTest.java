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

package org.openkilda.floodlight.command.flow.ingress;

import static java.util.stream.StreamSupport.stream;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FORWARD_UDP_PORT;
import static org.projectfloodlight.openflow.protocol.OFInstructionType.APPLY_ACTIONS;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata.RoutingMetadataBuilder;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata32;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowPushVxlanTunnel;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmEthDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmEthSrc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmUdpDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmUdpSrc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanVid;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class IngressServer42FlowInstallCommandTest {

    public static final SwitchId INGRESS_SWITCH_ID = new SwitchId(1);
    public static final SwitchId EGRESS_SWITCH_ID = new SwitchId(2);
    public static final int SERVER_42_PORT = 42;
    public static final int VLAN_1 = 1;
    public static final int VLAN_2 = 2;
    public static final int CUSTOMER_PORT = 3;
    public static final int ISL_PORT = 4;
    public static final int SERVER_42_UPD_PORT_OFFSET = 5000;
    public static final String SERVER_42_UPD_PORT_OFFSET_PROPERTY = "server42-upd-port-offset";
    public static final String FLOW_ID = "flow1";
    public static final org.openkilda.model.MacAddress SERVER_42_MAC_ADDRESS = new org.openkilda.model.MacAddress(
            "42:42:42:42:42:42");
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION =
            new FlowTransitEncapsulation(10, FlowEncapsulationType.TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION =
            new FlowTransitEncapsulation(11, FlowEncapsulationType.VXLAN);
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(
            FlowPathDirection.FORWARD, 10).toBuilder()
            .type(CookieType.SERVER_42_INGRESS)
            .build();
    public static final HashSet<SwitchFeature> FEATURES = Sets.newHashSet(SwitchFeature.HALF_SIZE_METADATA);

    @Test
    public void server42IngressFlowDoubleTagMultiTableVlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(VLAN_1, VLAN_2, VLAN_ENCAPSULATION, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonMultiTable(mod);
        assertEquals(3, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertEquals(VLAN_2, mod.getMatch().get(MatchField.VLAN_VID).getVlan());
        assertMetadata(mod, VLAN_1, CUSTOMER_PORT);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(4, applyActions.size());
        assertSetField(applyActions.get(0), OFOxmEthSrc.class, MacAddress.of(INGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(1), OFOxmEthDst.class, MacAddress.of(EGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(2), OFOxmVlanVid.class, OFVlanVidMatch.ofVlan(VLAN_ENCAPSULATION.getId()));
        assertOutputAction(applyActions.get(3));
    }

    @Test
    public void server42IngressFlowSingleTagMultiTableVlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(VLAN_1, 0, VLAN_ENCAPSULATION, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonMultiTable(mod);
        assertEquals(2, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertNull(mod.getMatch().get(MatchField.VLAN_VID));
        assertMetadata(mod, VLAN_1, CUSTOMER_PORT);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(5, applyActions.size());
        assertSetField(applyActions.get(0), OFOxmEthSrc.class, MacAddress.of(INGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(1), OFOxmEthDst.class, MacAddress.of(EGRESS_SWITCH_ID.toMacAddress()));
        assertPushVlanAction(applyActions.get(2));
        assertSetField(applyActions.get(3), OFOxmVlanVid.class, OFVlanVidMatch.ofVlan(VLAN_ENCAPSULATION.getId()));
        assertOutputAction(applyActions.get(4));
    }

    @Test
    public void server42IngressFlowDefaultMultiTableVlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(0, 0, VLAN_ENCAPSULATION, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonMultiTable(mod);
        assertEquals(2, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertNull(mod.getMatch().get(MatchField.VLAN_VID));
        assertMetadata(mod, 0, CUSTOMER_PORT);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(5, applyActions.size());
        assertSetField(applyActions.get(0), OFOxmEthSrc.class, MacAddress.of(INGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(1), OFOxmEthDst.class, MacAddress.of(EGRESS_SWITCH_ID.toMacAddress()));
        assertPushVlanAction(applyActions.get(2));
        assertSetField(applyActions.get(3), OFOxmVlanVid.class, OFVlanVidMatch.ofVlan(VLAN_ENCAPSULATION.getId()));
        assertOutputAction(applyActions.get(4));
    }

    @Test
    public void server42IngressFlowDoubleTagMultiTableVxlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(VLAN_1, VLAN_2, VXLAN_ENCAPSULATION, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonMultiTable(mod);
        assertEquals(3, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertEquals(VLAN_2, mod.getMatch().get(MatchField.VLAN_VID).getVlan());
        assertMetadata(mod, VLAN_1, CUSTOMER_PORT);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(2, applyActions.size());
        assertPushVxlanAction(applyActions.get(0));
        assertOutputAction(applyActions.get(1));
    }

    @Test
    public void server42IngressFlowSigleTagMultiTableVxlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(VLAN_1, 0, VXLAN_ENCAPSULATION, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonMultiTable(mod);
        assertEquals(2, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertNull(mod.getMatch().get(MatchField.VLAN_VID));
        assertMetadata(mod, VLAN_1, CUSTOMER_PORT);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(3, applyActions.size());
        assertPushVlanAction(applyActions.get(0));
        assertPushVxlanAction(applyActions.get(1));
        assertOutputAction(applyActions.get(2));
    }

    @Test
    public void server42IngressFlowDefaultMultiTableVxlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(0, 0, VXLAN_ENCAPSULATION, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonMultiTable(mod);
        assertEquals(2, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertNull(mod.getMatch().get(MatchField.VLAN_VID));
        assertMetadata(mod, 0, CUSTOMER_PORT);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(3, applyActions.size());
        assertPushVlanAction(applyActions.get(0));
        assertPushVxlanAction(applyActions.get(1));
        assertOutputAction(applyActions.get(2));
    }

    @Test
    public void server42IngressFlowSingleTagSingleTableVlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(VLAN_1, 0, VLAN_ENCAPSULATION, false);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonSingleTable(mod);
        assertEquals(6, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertEquals(VLAN_1, mod.getMatch().get(MatchField.VLAN_VID).getVlan());

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(6, applyActions.size());
        assertSetField(applyActions.get(0), OFOxmEthSrc.class, MacAddress.of(INGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(1), OFOxmEthDst.class, MacAddress.of(EGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(2), OFOxmUdpSrc.class, TransportPort.of(SERVER_42_FORWARD_UDP_PORT));
        assertSetField(applyActions.get(3), OFOxmUdpDst.class, TransportPort.of(SERVER_42_FORWARD_UDP_PORT));
        assertSetField(applyActions.get(4), OFOxmVlanVid.class, OFVlanVidMatch.ofVlan(VLAN_ENCAPSULATION.getId()));
        assertOutputAction(applyActions.get(5));
    }

    @Test
    public void server42IngressFlowDefaultSingleTableVlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(0, 0, VLAN_ENCAPSULATION, false);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonSingleTable(mod);
        assertEquals(5, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertNull(mod.getMatch().get(MatchField.VLAN_VID));

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(7, applyActions.size());
        assertSetField(applyActions.get(0), OFOxmEthSrc.class, MacAddress.of(INGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(1), OFOxmEthDst.class, MacAddress.of(EGRESS_SWITCH_ID.toMacAddress()));
        assertSetField(applyActions.get(2), OFOxmUdpSrc.class, TransportPort.of(SERVER_42_FORWARD_UDP_PORT));
        assertSetField(applyActions.get(3), OFOxmUdpDst.class, TransportPort.of(SERVER_42_FORWARD_UDP_PORT));
        assertPushVlanAction(applyActions.get(4));
        assertSetField(applyActions.get(5), OFOxmVlanVid.class, OFVlanVidMatch.ofVlan(VLAN_ENCAPSULATION.getId()));
        assertOutputAction(applyActions.get(6));
    }

    @Test
    public void server42IngressFlowSingleTagSingleTableVxlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(VLAN_1, 0, VXLAN_ENCAPSULATION, false);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonSingleTable(mod);
        assertEquals(6, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertEquals(VLAN_1, mod.getMatch().get(MatchField.VLAN_VID).getVlan());

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(2, applyActions.size());
        assertPushVxlanAction(applyActions.get(0));
        assertOutputAction(applyActions.get(1));
    }

    @Test
    public void server42IngressFlowDefaultSingleTableVxlan() throws Exception {
        IngressServer42FlowInstallCommand command = createCommand(0, 0, VXLAN_ENCAPSULATION, false);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(null));

        assertCommonSingleTable(mod);
        assertEquals(5, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertNull(mod.getMatch().get(MatchField.VLAN_VID));

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(3, applyActions.size());
        assertPushVlanAction(applyActions.get(0));
        assertPushVxlanAction(applyActions.get(1));
        assertOutputAction(applyActions.get(2));
    }

    private void assertMetadata(OFFlowMod mod, int expectedOuterVlan, int expectedPort) {
        RoutingMetadataBuilder metadataBuilder = RoutingMetadata32.builder()
                .inputPort(expectedPort);
        if (expectedOuterVlan != 0) {
            metadataBuilder.outerVlanId(expectedOuterVlan);
        }
        RoutingMetadata metadata = metadataBuilder.build(FEATURES);
        assertEquals(metadata.getValue(), mod.getMatch().getMasked(MatchField.METADATA).getValue().getValue());
        assertEquals(metadata.getMask(), mod.getMatch().getMasked(MatchField.METADATA).getMask().getValue());
    }

    private void assertCommonMultiTable(OFFlowMod mod) {
        assertCommon(mod);
        assertEquals(SwitchManager.INGRESS_TABLE_ID, mod.getTableId().getValue());
    }

    private void assertCommonSingleTable(OFFlowMod mod) {
        assertCommon(mod);
        assertEquals(0, mod.getTableId().getValue());
        assertEquals(MacAddress.of(SERVER_42_MAC_ADDRESS.getAddress()), mod.getMatch().get(MatchField.ETH_SRC));
        assertEquals(EthType.IPv4, mod.getMatch().get(MatchField.ETH_TYPE));
        assertEquals(IpProtocol.UDP, mod.getMatch().get(MatchField.IP_PROTO));
        assertEquals(SERVER_42_UPD_PORT_OFFSET + CUSTOMER_PORT, mod.getMatch().get(MatchField.UDP_SRC).getPort());
    }

    private void assertCommon(OFFlowMod mod) {
        assertEquals(COOKIE.getValue(), mod.getCookie().getValue());
        assertEquals(SERVER_42_PORT, mod.getMatch().get(MatchField.IN_PORT).getPortNumber());
        assertEquals(1, mod.getInstructions().size());
        assertEquals(APPLY_ACTIONS, mod.getInstructions().get(0).getType());
    }

    private void assertOutputAction(OFAction action) {
        assertEquals(OFActionType.OUTPUT, action.getType());
        assertEquals(ISL_PORT, ((OFActionOutput) action).getPort().getPortNumber());
    }

    private void assertPushVxlanAction(OFAction action) {
        assertEquals(OFActionType.EXPERIMENTER, action.getType());
        assertTrue(action instanceof OFActionNoviflowPushVxlanTunnel);
        OFActionNoviflowPushVxlanTunnel pushVxlan = (OFActionNoviflowPushVxlanTunnel) action;
        assertEquals(MacAddress.of(INGRESS_SWITCH_ID.toMacAddress()), pushVxlan.getEthSrc());
        assertEquals(MacAddress.of(EGRESS_SWITCH_ID.toMacAddress()), pushVxlan.getEthDst());
        assertEquals(SERVER_42_FORWARD_UDP_PORT, pushVxlan.getUdpSrc());
        assertEquals(VXLAN_ENCAPSULATION.getId().intValue(), pushVxlan.getVni());
    }

    private void assertPushVlanAction(OFAction action) {
        assertEquals(OFActionType.PUSH_VLAN, action.getType());
        assertEquals(EthType.VLAN_FRAME, ((OFActionPushVlan) action).getEthertype());
    }

    private void assertSetField(OFAction action, Class<? extends OFOxm> type, Object value) {
        assertEquals(OFActionType.SET_FIELD, action.getType());
        OFActionSetField setFiledAction = (OFActionSetField) action;
        assertTrue(type.isInstance(setFiledAction.getField()));
        assertEquals(value, setFiledAction.getField().getValue());
    }

    private OFFlowMod assertModCountAndReturnMod(List<OFFlowMod> mods) {
        assertEquals(1, mods.size());
        return mods.get(0);
    }

    private IngressServer42FlowInstallCommand createCommand(
            int outerVlan, int innerVlan, FlowTransitEncapsulation encapsulation, boolean multiTable) throws Exception {
        FlowSegmentMetadata metadata = new FlowSegmentMetadata(FLOW_ID, COOKIE, multiTable);

        IngressServer42FlowInstallCommand command = new IngressServer42FlowInstallCommand(new MessageContext(),
                UUID.randomUUID(), metadata, INGRESS_SWITCH_ID, CUSTOMER_PORT, outerVlan, innerVlan,
                EGRESS_SWITCH_ID, ISL_PORT, encapsulation, SERVER_42_PORT, SERVER_42_MAC_ADDRESS);

        FloodlightModuleContext context = new FloodlightModuleContext();
        IOFSwitchService switchServiceMock = mock(IOFSwitchService.class);
        expect(switchServiceMock.getActiveSwitch(anyObject())).andReturn(getOfSwitch()).anyTimes();
        context.addService(IOFSwitchService.class, switchServiceMock);

        FeatureDetectorService featureDetectorServiceMock = mock(FeatureDetectorService.class);
        expect(featureDetectorServiceMock.detectSwitch(anyObject())).andReturn(FEATURES).anyTimes();
        context.addService(FeatureDetectorService.class, featureDetectorServiceMock);

        Properties properties = new Properties();
        properties.setProperty(SERVER_42_UPD_PORT_OFFSET_PROPERTY, Integer.toString(SERVER_42_UPD_PORT_OFFSET));
        PropertiesBasedConfigurationProvider provider = new PropertiesBasedConfigurationProvider(properties);

        KildaCore kildaCoreMock = mock(KildaCore.class);
        expect(kildaCoreMock.getConfig()).andReturn(provider.getConfiguration(KildaCoreConfig.class)).anyTimes();
        context.addService(KildaCore.class, kildaCoreMock);

        replay(switchServiceMock, featureDetectorServiceMock, kildaCoreMock);
        command.setup(context);
        return command;
    }

    private OFSwitch getOfSwitch() {
        OFSwitch sw = mock(OFSwitch.class);
        expect(sw.getOFFactory()).andReturn(new OFFactoryVer13()).anyTimes();
        expect(sw.getId()).andReturn(DatapathId.of(INGRESS_SWITCH_ID.getId())).anyTimes();
        replay(sw);
        return sw;
    }
}
