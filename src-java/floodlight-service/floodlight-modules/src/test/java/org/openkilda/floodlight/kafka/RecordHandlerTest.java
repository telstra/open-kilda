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

package org.openkilda.floodlight.kafka;

import static java.util.Collections.emptyList;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.niceMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.openkilda.messaging.command.switches.InstallRulesAction.INSTALL_DEFAULTS;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE;
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVER_42_FLOW_RTT_INPUT;
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVER_42_ISL_RTT_INPUT;

import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.InstallServer42Flow;
import org.openkilda.messaging.command.flow.ReinstallServer42FlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;

import com.google.common.collect.Lists;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.DatapathId;

public class RecordHandlerTest {
    public static final SwitchId SWITCH_ID = new SwitchId(1);
    public static final DatapathId DATAPATH_ID = DatapathId.of(SWITCH_ID.getId());
    public static final int CUSTOMER_PORT = 15;
    public static final int SERVER42_PORT = 42;
    public static final int SERVER42_VLAN = 2;
    public static final int VLAN_1 = 1;
    public static final MacAddress SERVER42_MAC_ADDRESS = new MacAddress("42:42:42:42:42:42");
    public static final String CORRELATION_ID = "corr";

    RecordHandler recordHandler;
    ISwitchManager switchManager;

    @Before
    public void setUp() throws Exception {
        switchManager = mock(ISwitchManager.class);
        FloodlightModuleContext floodlightModuleContext = new FloodlightModuleContext();
        floodlightModuleContext.addService(IKafkaProducerService.class, mock(IKafkaProducerService.class));

        ConsumerContext context = mock(ConsumerContext.class);
        expect(context.getSwitchManager()).andReturn(switchManager).anyTimes();
        expect(context.getModuleContext()).andReturn(floodlightModuleContext).anyTimes();
        expect(context.getKafkaSwitchManagerTopic()).andReturn("").anyTimes();
        replay(context);

        ConsumerRecord record = niceMock(ConsumerRecord.class);
        recordHandler = new RecordHandler(context, null, record);
    }

    @Test
    public void reinstallServer42FlowRttOutputVlanTest() throws SwitchOperationException {
        long cookie = SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE;
        expect(switchManager.installServer42FlowRttOutputVlanFlow(
                DATAPATH_ID, SERVER42_PORT, SERVER42_VLAN, SERVER42_MAC_ADDRESS)).andReturn(cookie).once();
        expect(switchManager.deleteRulesByCriteria(DATAPATH_ID, false, null, DeleteRulesCriteria.builder()
                .cookie(cookie).build()))
                .andReturn(Lists.newArrayList(cookie)).once();
        replay(switchManager);

        ReinstallServer42FlowForSwitchManagerRequest request = new ReinstallServer42FlowForSwitchManagerRequest(
                SWITCH_ID, cookie, SERVER42_MAC_ADDRESS, SERVER42_VLAN, SERVER42_PORT);

        recordHandler.handleCommand(new CommandMessage(request, 0, null));
        verify(switchManager);
    }

    @Test
    public void reinstallServer42FlowRttOutputVxlanTest() throws SwitchOperationException {
        long cookie = SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
        expect(switchManager.installServer42FlowRttOutputVxlanFlow(
                DATAPATH_ID, SERVER42_PORT, SERVER42_VLAN, SERVER42_MAC_ADDRESS)).andReturn(cookie).once();
        expect(switchManager.deleteRulesByCriteria(DATAPATH_ID, false, null, DeleteRulesCriteria.builder()
                .cookie(cookie).build()))
                .andReturn(Lists.newArrayList(cookie)).once();
        replay(switchManager);

        ReinstallServer42FlowForSwitchManagerRequest request = new ReinstallServer42FlowForSwitchManagerRequest(
                SWITCH_ID, cookie, SERVER42_MAC_ADDRESS, SERVER42_VLAN, SERVER42_PORT);

        recordHandler.handleCommand(new CommandMessage(request, 0, null));
        verify(switchManager);
    }


    @Test
    public void reinstallServer42FlowRttInputTest() throws SwitchOperationException {
        PortColourCookie cookie = PortColourCookie.builder()
                .portNumber(CUSTOMER_PORT)
                .type(SERVER_42_FLOW_RTT_INPUT)
                .build();

        expect(switchManager.installServer42FlowRttInputFlow(
                DATAPATH_ID, SERVER42_PORT, CUSTOMER_PORT, SERVER42_MAC_ADDRESS)).andReturn(cookie.getValue()).once();
        expect(switchManager.deleteRulesByCriteria(DATAPATH_ID, false, null, DeleteRulesCriteria.builder()
                .cookie(cookie.getValue()).build()))
                .andReturn(Lists.newArrayList(cookie.getValue())).once();
        replay(switchManager);

        ReinstallServer42FlowForSwitchManagerRequest request = new ReinstallServer42FlowForSwitchManagerRequest(
                SWITCH_ID, cookie.getValue(), SERVER42_MAC_ADDRESS, SERVER42_VLAN, SERVER42_PORT);

        recordHandler.handleCommand(new CommandMessage(request, 0, null));
        verify(switchManager);
    }

    @Test
    public void installServer42OutputVlanTest() throws SwitchOperationException {
        long cookie = SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE;
        expect(switchManager.installServer42FlowRttOutputVlanFlow(
                DATAPATH_ID, SERVER42_PORT, SERVER42_VLAN, SERVER42_MAC_ADDRESS)).andReturn(cookie).once();
        replay(switchManager);

        InstallServer42Flow request = InstallServer42Flow.builder()
                .id("output_vlan")
                .switchId(SWITCH_ID)
                .cookie(cookie)
                .inputPort(0)
                .outputPort(SERVER42_PORT)
                .server42MacAddress(SERVER42_MAC_ADDRESS)
                .server42Vlan(SERVER42_VLAN)
                .build();

        recordHandler.handleCommand(
                new CommandMessage(new InstallFlowForSwitchManagerRequest(request), 0, CORRELATION_ID));
        verify(switchManager);
    }

    @Test
    public void installServer42OutputVxlanTest() throws SwitchOperationException {
        long cookie = SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
        expect(switchManager.installServer42FlowRttOutputVxlanFlow(
                DATAPATH_ID, SERVER42_PORT, SERVER42_VLAN, SERVER42_MAC_ADDRESS)).andReturn(cookie).once();
        replay(switchManager);

        InstallServer42Flow request = InstallServer42Flow.builder()
                .id("output_vxlan")
                .switchId(SWITCH_ID)
                .cookie(cookie)
                .inputPort(0)
                .outputPort(SERVER42_PORT)
                .server42MacAddress(SERVER42_MAC_ADDRESS)
                .server42Vlan(SERVER42_VLAN)
                .build();

        recordHandler.handleCommand(
                new CommandMessage(new InstallFlowForSwitchManagerRequest(request), 0, CORRELATION_ID));
        verify(switchManager);
    }

    @Test
    public void installServer42InputTest() throws SwitchOperationException {
        PortColourCookie cookie = PortColourCookie.builder()
                .portNumber(CUSTOMER_PORT)
                .type(SERVER_42_FLOW_RTT_INPUT)
                .build();

        expect(switchManager.installServer42FlowRttInputFlow(
                DATAPATH_ID, SERVER42_PORT, CUSTOMER_PORT, SERVER42_MAC_ADDRESS)).andReturn(cookie.getValue()).once();
        replay(switchManager);

        InstallServer42Flow request = InstallServer42Flow.builder()
                .id("input")
                .switchId(SWITCH_ID)
                .cookie(cookie.getValue())
                .inputPort(SERVER42_PORT)
                .outputPort(0)
                .server42MacAddress(SERVER42_MAC_ADDRESS)
                .server42Vlan(SERVER42_VLAN)
                .build();

        recordHandler.handleCommand(
                new CommandMessage(new InstallFlowForSwitchManagerRequest(request), 0, CORRELATION_ID));
        verify(switchManager);
    }

    @Test
    public void installServer42SharedTest() throws SwitchOperationException {
        FlowSharedSegmentCookie cookie = FlowSharedSegmentCookie
                .builder(SharedSegmentType.SERVER42_QINQ_OUTER_VLAN)
                .portNumber(SERVER42_PORT)
                .vlanId(VLAN_1)
                .build();

        switchManager.installServer42OuterVlanMatchSharedFlow(DATAPATH_ID, cookie);
        expectLastCall().once();
        replay(switchManager);

        InstallServer42Flow request = InstallServer42Flow.builder()
                .id("shared")
                .switchId(SWITCH_ID)
                .cookie(cookie.getValue())
                .inputPort(SERVER42_PORT)
                .outputPort(0)
                .build();

        recordHandler.handleCommand(
                new CommandMessage(new InstallFlowForSwitchManagerRequest(request), 0, CORRELATION_ID));
        verify(switchManager);
    }

    @Test
    public void shouldReinstallServer42IslRttOutputRule() throws SwitchOperationException {
        long cookie = SERVER_42_ISL_RTT_OUTPUT_COOKIE;
        expect(switchManager.installServer42IslRttOutputFlow(
                DATAPATH_ID, SERVER42_PORT, SERVER42_VLAN, SERVER42_MAC_ADDRESS)).andReturn(cookie).once();
        expect(switchManager.deleteRulesByCriteria(DATAPATH_ID, false, null, DeleteRulesCriteria.builder()
                .cookie(cookie).build()))
                .andReturn(Lists.newArrayList(cookie)).once();
        replay(switchManager);

        ReinstallServer42FlowForSwitchManagerRequest request = new ReinstallServer42FlowForSwitchManagerRequest(
                SWITCH_ID, cookie, SERVER42_MAC_ADDRESS, SERVER42_VLAN, SERVER42_PORT);

        recordHandler.handleCommand(new CommandMessage(request, 0, null));
        verify(switchManager);
    }

    @Test
    public void shouldReinstallServer42IslRttInputRule() throws SwitchOperationException {
        int islPort = 123;
        PortColourCookie cookie = PortColourCookie.builder()
                .portNumber(islPort)
                .type(SERVER_42_ISL_RTT_INPUT)
                .build();

        expect(switchManager.installServer42IslRttInputFlow(
                DATAPATH_ID, SERVER42_PORT, islPort)).andReturn(cookie.getValue()).once();
        expect(switchManager.deleteRulesByCriteria(DATAPATH_ID, false, null, DeleteRulesCriteria.builder()
                .cookie(cookie.getValue()).build()))
                .andReturn(Lists.newArrayList(cookie.getValue())).once();
        replay(switchManager);

        ReinstallServer42FlowForSwitchManagerRequest request = new ReinstallServer42FlowForSwitchManagerRequest(
                SWITCH_ID, cookie.getValue(), SERVER42_MAC_ADDRESS, SERVER42_VLAN, SERVER42_PORT);

        recordHandler.handleCommand(new CommandMessage(request, 0, null));
        verify(switchManager);
    }

    @Test
    public void shouldInstallServer42IslRttOutputRule() throws SwitchOperationException {
        long cookie = SERVER_42_ISL_RTT_OUTPUT_COOKIE;
        expect(switchManager.installServer42IslRttOutputFlow(
                DATAPATH_ID, SERVER42_PORT, SERVER42_VLAN, SERVER42_MAC_ADDRESS)).andReturn(cookie).once();
        replay(switchManager);

        InstallServer42Flow request = InstallServer42Flow.builder()
                .id("test")
                .switchId(SWITCH_ID)
                .cookie(cookie)
                .inputPort(0)
                .outputPort(SERVER42_PORT)
                .server42MacAddress(SERVER42_MAC_ADDRESS)
                .server42Vlan(SERVER42_VLAN)
                .build();

        recordHandler.handleCommand(
                new CommandMessage(new InstallFlowForSwitchManagerRequest(request), 0, CORRELATION_ID));
        verify(switchManager);
    }

    @Test
    public void shouldInstallServer42IslRttInputRule() throws SwitchOperationException {
        int islPort = 123;
        PortColourCookie cookie = PortColourCookie.builder()
                .portNumber(islPort)
                .type(SERVER_42_ISL_RTT_INPUT)
                .build();

        expect(switchManager.installServer42IslRttInputFlow(
                DATAPATH_ID, SERVER42_PORT, islPort)).andReturn(cookie.getValue()).once();
        replay(switchManager);

        InstallServer42Flow request = InstallServer42Flow.builder()
                .id("test")
                .switchId(SWITCH_ID)
                .cookie(cookie.getValue())
                .inputPort(SERVER42_PORT)
                .outputPort(0)
                .server42MacAddress(SERVER42_MAC_ADDRESS)
                .server42Vlan(SERVER42_VLAN)
                .build();

        recordHandler.handleCommand(
                new CommandMessage(new InstallFlowForSwitchManagerRequest(request), 0, CORRELATION_ID));
        verify(switchManager);
    }

    @Test
    public void shouldInstallServer42IslRttRulesOnInstallDefaults() throws SwitchOperationException {
        expect(switchManager.installDefaultRules(DATAPATH_ID))
                .andReturn(emptyList()).once();
        expect(switchManager.installServer42IslRttOutputFlow(
                DATAPATH_ID, SERVER42_PORT, SERVER42_VLAN, SERVER42_MAC_ADDRESS))
                .andReturn(SERVER_42_ISL_RTT_OUTPUT_COOKIE).once();
        expect(switchManager.installServer42IslRttTurningFlow(DATAPATH_ID))
                .andReturn(SERVER_42_ISL_RTT_TURNING_COOKIE).once();
        replay(switchManager);

        SwitchRulesInstallRequest request = new SwitchRulesInstallRequest(SWITCH_ID, INSTALL_DEFAULTS);
        request.setServer42IslRttEnabled(true);
        request.setServer42Port(SERVER42_PORT);
        request.setServer42Vlan(SERVER42_VLAN);
        request.setServer42MacAddress(SERVER42_MAC_ADDRESS);
        recordHandler.handleCommand(new CommandMessage(request, 0, CORRELATION_ID));

        verify(switchManager);
    }
}
