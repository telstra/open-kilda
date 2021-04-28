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
import static org.openkilda.floodlight.switchmanager.SwitchManager.INGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INPUT_TABLE_ID;
import static org.openkilda.model.SwitchFeature.HALF_SIZE_METADATA;
import static org.openkilda.model.SwitchFeature.MULTI_TABLE;
import static org.projectfloodlight.openflow.protocol.OFInstructionType.APPLY_ACTIONS;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.model.EffectiveIds;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata.RoutingMetadataBuilder;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata32;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanVid;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class IngressFlowLoopSegmentInstallCommandTest {

    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final int VLAN_1 = 1;
    public static final int VLAN_2 = 2;
    public static final int PORT_1 = 3;
    public static final int ISL_PORT = 4;
    public static final String FLOW_ID = "flow1";
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(
            FlowPathDirection.FORWARD, 10).toBuilder()
            .looped(true)
            .build();
    public static final HashSet<SwitchFeature> FEATURES = Sets.newHashSet(HALF_SIZE_METADATA, MULTI_TABLE);

    @Test
    public void ingressFlowLoopDoubleTagMultiTableTest() throws Exception {
        IngressFlowLoopSegmentInstallCommand command = createCommand(VLAN_1, VLAN_2, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(new EffectiveIds()));

        assertCommon(mod, INGRESS_TABLE_ID);
        assertEquals(3, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertEquals(VLAN_2, mod.getMatch().get(MatchField.VLAN_VID).getVlan());
        assertMetadata(mod.getMatch(), VLAN_1);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(3, applyActions.size());
        assertPushVlanAction(applyActions.get(0));
        assertSetField(applyActions.get(1), OFOxmVlanVid.class, OFVlanVidMatch.ofVlan(VLAN_1));
        assertOutputAction(applyActions.get(2));
    }

    @Test
    public void ingressFlowLoopSigleTagMultiTableTest() throws Exception {
        IngressFlowLoopSegmentInstallCommand command = createCommand(VLAN_1, 0, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(new EffectiveIds()));

        assertCommon(mod, INGRESS_TABLE_ID);
        assertEquals(2, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertNull(mod.getMatch().get(MatchField.VLAN_VID));
        assertMetadata(mod.getMatch(), VLAN_1);

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(3, applyActions.size());
        assertPushVlanAction(applyActions.get(0));
        assertSetField(applyActions.get(1), OFOxmVlanVid.class, OFVlanVidMatch.ofVlan(VLAN_1));
        assertOutputAction(applyActions.get(2));
    }

    @Test
    public void ingressFlowLoopDefaultTagMultiTableTest() throws Exception {
        IngressFlowLoopSegmentInstallCommand command = createCommand(0, 0, true);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(new EffectiveIds()));

        assertCommon(mod, INGRESS_TABLE_ID);
        assertEquals(1, stream(mod.getMatch().getMatchFields().spliterator(), false).count());

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(1, applyActions.size());
        assertOutputAction(applyActions.get(0));
    }

    @Test
    public void ingressFlowLoopSigleTagSingleTableTest() throws Exception {
        IngressFlowLoopSegmentInstallCommand command = createCommand(VLAN_1, 0, false);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(new EffectiveIds()));

        assertCommon(mod, INPUT_TABLE_ID);
        assertEquals(2, stream(mod.getMatch().getMatchFields().spliterator(), false).count());
        assertEquals(VLAN_1, mod.getMatch().get(MatchField.VLAN_VID).getVlan());

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(1, applyActions.size());
        assertOutputAction(applyActions.get(0));
    }

    @Test
    public void ingressFlowLoopDefaultTagSingleTableTest() throws Exception {
        IngressFlowLoopSegmentInstallCommand command = createCommand(0, 0, false);
        OFFlowMod mod = assertModCountAndReturnMod(command.makeFlowModMessages(new EffectiveIds()));

        assertCommon(mod, INPUT_TABLE_ID);
        assertEquals(1, stream(mod.getMatch().getMatchFields().spliterator(), false).count());

        List<OFAction> applyActions = ((OFInstructionApplyActions) mod.getInstructions().get(0)).getActions();
        assertEquals(1, applyActions.size());
        assertOutputAction(applyActions.get(0));
    }


    private OFFlowMod assertModCountAndReturnMod(List<OFFlowMod> mods) {
        assertEquals(1, mods.size());
        return mods.get(0);
    }

    private void assertMetadata(Match match, int expectedOuterVlan) {
        RoutingMetadataBuilder metadataBuilder = RoutingMetadata32.builder();
        metadataBuilder.outerVlanId(expectedOuterVlan);
        RoutingMetadata metadata = metadataBuilder.build(FEATURES);
        assertEquals(metadata.getValue(), match.getMasked(MatchField.METADATA).getValue().getValue());
        assertEquals(metadata.getMask(), match.getMasked(MatchField.METADATA).getMask().getValue());
    }

    private void assertCommon(OFFlowMod mod, int tableId) {
        assertEquals(tableId, mod.getTableId().getValue());
        assertEquals(COOKIE.getValue(), mod.getCookie().getValue());
        assertEquals(PORT_1, mod.getMatch().get(MatchField.IN_PORT).getPortNumber());
        assertEquals(1, mod.getInstructions().size());
        assertEquals(APPLY_ACTIONS, mod.getInstructions().get(0).getType());
    }

    private void assertOutputAction(OFAction action) {
        assertEquals(OFActionType.OUTPUT, action.getType());
        assertEquals(OFPort.IN_PORT, ((OFActionOutput) action).getPort());
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

    private IngressFlowLoopSegmentInstallCommand createCommand(
            int outerVlan, int innerVlan, boolean multiTable) throws Exception {
        FlowSegmentMetadata metadata = new FlowSegmentMetadata(FLOW_ID, COOKIE, multiTable);
        FlowEndpoint endpoint = FlowEndpoint.builder()
                .switchId(SWITCH_ID_1)
                .portNumber(PORT_1)
                .outerVlanId(outerVlan)
                .innerVlanId(innerVlan)
                .build();

        IngressFlowLoopSegmentInstallCommand command = new IngressFlowLoopSegmentInstallCommand(
                new MessageContext(), UUID.randomUUID(), metadata, endpoint);

        FloodlightModuleContext context = new FloodlightModuleContext();
        IOFSwitchService switchServiceMock = mock(IOFSwitchService.class);
        expect(switchServiceMock.getActiveSwitch(anyObject())).andReturn(getOfSwitch()).anyTimes();
        context.addService(IOFSwitchService.class, switchServiceMock);

        FeatureDetectorService featureDetectorServiceMock = mock(FeatureDetectorService.class);
        expect(featureDetectorServiceMock.detectSwitch(anyObject())).andReturn(FEATURES).anyTimes();
        context.addService(FeatureDetectorService.class, featureDetectorServiceMock);

        PropertiesBasedConfigurationProvider provider = new PropertiesBasedConfigurationProvider(new Properties());

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
        expect(sw.getId()).andReturn(DatapathId.of(SWITCH_ID_1.getId())).anyTimes();
        replay(sw);
        return sw;
    }
}
