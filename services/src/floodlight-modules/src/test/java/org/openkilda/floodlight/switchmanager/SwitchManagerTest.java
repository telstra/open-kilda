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

package org.openkilda.floodlight.switchmanager;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertArrayEquals;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Every.everyItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.openkilda.floodlight.Constants.bandwidth;
import static org.openkilda.floodlight.Constants.burstSize;
import static org.openkilda.floodlight.Constants.inputPort;
import static org.openkilda.floodlight.Constants.inputVlanId;
import static org.openkilda.floodlight.Constants.meterId;
import static org.openkilda.floodlight.Constants.outputPort;
import static org.openkilda.floodlight.Constants.outputVlanId;
import static org.openkilda.floodlight.Constants.transitVlanId;
import static org.openkilda.floodlight.message.command.encapsulation.PushSchemeOutputCommands.ofFactory;
import static org.openkilda.floodlight.switchmanager.ISwitchManager.DROP_RULE_COOKIE;
import static org.openkilda.floodlight.switchmanager.ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.floodlight.switchmanager.ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE;

import com.google.common.collect.Lists;
import com.google.common.hash.PrimitiveSink;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.buffer.ByteBuf;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import org.apache.commons.codec.binary.Hex;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openkilda.floodlight.message.command.encapsulation.OutputCommands;
import org.openkilda.floodlight.message.command.encapsulation.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.payload.flow.OutputVlanType;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowBfdStart;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.types.*;

import javax.xml.crypto.Data;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class SwitchManagerTest {
    private static final OutputCommands scheme = new ReplaceSchemeOutputCommands();
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private static final long cookie = 123L;
    private static final String cookieHex = "7B";
    private SwitchManager switchManager;
    private IOFSwitchService ofSwitchService;
    private IRestApiService restApiService;
    private IOFSwitch iofSwitch;
    private SwitchDescription switchDescription;
    private DatapathId dpid;

    @Before
    public void setUp() throws FloodlightModuleException {
        ofSwitchService = createMock(IOFSwitchService.class);
        restApiService = createMock(IRestApiService.class);
        iofSwitch = createMock(IOFSwitch.class);
        switchDescription = createMock(SwitchDescription.class);
        dpid = createMock(DatapathId.class);

        context.addService(IRestApiService.class, restApiService);
        context.addService(IOFSwitchService.class, ofSwitchService);

        switchManager = new SwitchManager();
        switchManager.init(context);
    }

    @Test
    public void installDefaultRules() throws Exception {
        // TODO
    }

    @Test
    public void installIngressFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, inputVlanId, transitVlanId, OutputVlanType.REPLACE, meterId);

        assertEquals(
                scheme.ingressReplaceFlowMod(inputPort, outputPort, inputVlanId, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installIngressFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, inputVlanId, transitVlanId, OutputVlanType.POP, meterId);

        assertEquals(
                scheme.ingressPopFlowMod(inputPort, outputPort, inputVlanId, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installIngressFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, 0, transitVlanId, OutputVlanType.PUSH, meterId);

        assertEquals(
                scheme.ingressPushFlowMod(inputPort, outputPort, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installIngressFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, 0, transitVlanId, OutputVlanType.NONE, meterId);

        assertEquals(
                scheme.ingressNoneFlowMod(inputPort, outputPort, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, transitVlanId, 0, OutputVlanType.NONE);

        assertEquals(
                scheme.egressNoneFlowMod(inputPort, outputPort, transitVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, transitVlanId, outputVlanId, OutputVlanType.PUSH);

        assertEquals(
                scheme.egressPushFlowMod(inputPort, outputPort, transitVlanId, outputVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, transitVlanId, 0, OutputVlanType.POP);

        assertEquals(
                scheme.egressPopFlowMod(inputPort, outputPort, transitVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, transitVlanId, outputVlanId, OutputVlanType.REPLACE);

        assertEquals(
                scheme.egressReplaceFlowMod(inputPort, outputPort, transitVlanId, outputVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installTransitFlow() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installTransitFlow(dpid, cookieHex, cookie, inputPort, outputPort, transitVlanId);

        assertEquals(
                scheme.transitFlowMod(inputPort, outputPort, transitVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, inputVlanId, outputVlanId, OutputVlanType.REPLACE, meterId);

        assertEquals(
                scheme.oneSwitchReplaceFlowMod(inputPort, outputPort, inputVlanId, outputVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, 0, outputVlanId, OutputVlanType.PUSH, meterId);

        assertEquals(
                scheme.oneSwitchPushFlowMod(inputPort, outputPort, outputVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, inputVlanId, 0, OutputVlanType.POP, meterId);

        assertEquals(
                scheme.oneSwitchPopFlowMod(inputPort, outputPort, inputVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, 0, 0, OutputVlanType.NONE, meterId);

        assertEquals(
                scheme.oneSwitchNoneFlowMod(inputPort, outputPort, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void dumpFlowTable() throws Exception {
        // TODO
    }

    @Test
    public void dumpMeters() throws Exception {
        // TODO
    }

    @Test
    public void installBandwidthMeter() throws Exception {
        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(switchDescription.getManufacturerDescription()).andStubReturn("");

        expect(iofSwitch.write(scheme.installMeter(bandwidth, burstSize, meterId))).andReturn(true);
        expect(iofSwitch.writeRequest(anyObject(OFBarrierRequest.class)))
                .andReturn(Futures.immediateFuture(createMock(OFBarrierReply.class)));

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(switchDescription);

        switchManager.installMeter(dpid, bandwidth, burstSize, meterId);
    }

    @Test
    public void deleteMeter() throws SwitchOperationException {
        final Capture<OFMeterMod> capture = prepareForMeterTest();
        switchManager.deleteMeter(dpid, meterId);
        final OFMeterMod meterMod = capture.getValue();
        assertEquals(meterMod.getCommand(), OFMeterModCommand.DELETE);
        assertEquals(meterMod.getMeterId(), meterId);
    }

    @Test
    public void shouldDeleteAllNonDefaultRules() throws Exception {
        // given
        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture();
        expect(iofSwitch.write(capture(capture))).andReturn(true);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        List<Long> deletedRules = switchManager.deleteAllNonDefaultRules(dpid);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertNull(actual.getMatch().get(MatchField.IN_PORT));
        assertNull(actual.getMatch().get(MatchField.VLAN_VID));
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(cookie, actual.getCookie().getValue());
        assertEquals(U64.NO_MASK, actual.getCookieMask());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteDefaultRules() throws Exception {
        // given
        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(cookie);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        List<Long> deletedRules = switchManager.deleteDefaultRules(dpid);

        // then
        final List<OFFlowMod> actual = capture.getValues();
        assertEquals(3, actual.size());
        assertThat(actual, everyItem(hasProperty("command", equalTo(OFFlowModCommand.DELETE))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(DROP_RULE_COOKIE)))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(VERIFICATION_BROADCAST_RULE_COOKIE)))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(VERIFICATION_UNICAST_RULE_COOKIE)))));
        assertThat(deletedRules, containsInAnyOrder(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE));
    }

    @Test
    public void shouldDeleteRuleByCookie() throws Exception {
        // given
        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder().cookie(cookie).build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertNull(actual.getMatch().get(MatchField.IN_PORT));
        assertNull(actual.getMatch().get(MatchField.VLAN_VID));
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(cookie, actual.getCookie().getValue());
        assertEquals(U64.NO_MASK, actual.getCookieMask());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteRuleByInPort() throws Exception {
        // given
        final int testInPort = 11;

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder().inPort(testInPort).build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(testInPort, actual.getMatch().get(MatchField.IN_PORT).getPortNumber());
        assertNull(actual.getMatch().get(MatchField.VLAN_VID));
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(0L, actual.getCookie().getValue());
        assertEquals(0L, actual.getCookieMask().getValue());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteRuleByInVlan() throws Exception {
        // given
        final short testInVlan = 101;

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder().inVlan((int) testInVlan).build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(testInVlan, actual.getMatch().get(MatchField.VLAN_VID).getVlan());
        assertNull(actual.getMatch().get(MatchField.IN_PORT));
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(0L, actual.getCookie().getValue());
        assertEquals(0L, actual.getCookieMask().getValue());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteRuleByInPortAndVlan() throws Exception {
        // given
        final int testInPort = 11;
        final short testInVlan = 101;

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder()
                .inPort(testInPort)
                .inVlan((int) testInVlan).build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(testInPort, actual.getMatch().get(MatchField.IN_PORT).getPortNumber());
        assertEquals(testInVlan, actual.getMatch().get(MatchField.VLAN_VID).getVlan());
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(0L, actual.getCookie().getValue());
        assertEquals(0L, actual.getCookieMask().getValue());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteRuleByPriority() throws Exception {
        // given
        final int testPriority = 999;

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder().priority(testPriority).build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertNull(actual.getMatch().get(MatchField.IN_PORT));
        assertNull(actual.getMatch().get(MatchField.VLAN_VID));
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(0L, actual.getCookie().getValue());
        assertEquals(0L, actual.getCookieMask().getValue());
        assertEquals(testPriority, actual.getPriority());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteRuleByInPortVlanAndPriority() throws Exception {
        // given
        final int testInPort = 11;
        final short testInVlan = 101;
        final int testPriority = 999;

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder()
                .inPort(testInPort)
                .inVlan((int) testInVlan)
                .priority(testPriority).build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(testInPort, actual.getMatch().get(MatchField.IN_PORT).getPortNumber());
        assertEquals(testInVlan, actual.getMatch().get(MatchField.VLAN_VID).getVlan());
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(0L, actual.getCookie().getValue());
        assertEquals(0L, actual.getCookieMask().getValue());
        assertEquals(testPriority, actual.getPriority());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteRuleByOutPort() throws Exception {
        // given
        final int testOutPort = 21;

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(3);

        mockBarrierRequest();
        mockFlowStatsRequest(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE, VERIFICATION_UNICAST_RULE_COOKIE);
        expectLastCall();

        replay(ofSwitchService, iofSwitch);

        // when
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder().outPort(testOutPort).build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertNull(actual.getMatch().get(MatchField.IN_PORT));
        assertNull(actual.getMatch().get(MatchField.VLAN_VID));
        assertEquals(testOutPort, actual.getOutPort().getPortNumber());
        assertEquals(0L, actual.getCookie().getValue());
        assertEquals(0L, actual.getCookieMask().getValue());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void installBfdMatch() throws Exception {
        // given
        final String swDpid = "00:de:ad:be:ef:00";
        final String manufacture = "noviflow";
        IOFSwitch sw = createMock(IOFSwitch.class);
        SwitchDescription switchDescription = createMock(SwitchDescription.class);

        expect(ofSwitchService.getSwitch(DatapathId.of(swDpid))).andStubReturn(sw);
        expect(sw.getOFFactory()).andStubReturn(ofFactory);
        expect(sw.getId()).andStubReturn(DatapathId.of(swDpid));
        expect(sw.getSwitchDescription()).andStubReturn(switchDescription);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(manufacture);

        Capture <OFFlowMod> ofFlowModCapture = new Capture<>();
        expect(sw.write(capture(ofFlowModCapture))).andReturn(true);

        replay(sw, ofSwitchService, switchDescription);

        // when
        switchManager.installBfdMatch(sw.getId());

        // then
        Match match = ofFlowModCapture.getValue().getMatch();
        assertEquals(SwitchManager.UDP_BFD_PORT, match.get(MatchField.UDP_DST).getPort());
        assertEquals(MacAddress.of(swDpid), match.get(MatchField.ETH_DST));
        assertEquals(EthType.IPv4, match.get(MatchField.ETH_TYPE));
        assertEquals(IpProtocol.UDP, match.get(MatchField.IP_PROTO));

        List<OFAction> actions = ofFlowModCapture.getValue().getActions();
        assertEquals(1, actions.size());
        assertThat(actions.get(0), instanceOf(OFActionOutput.class));

        OFActionOutput output = (OFActionOutput) actions.get(0);
        assertEquals(OFPort.LOCAL, output.getPort());
    }


    @Test
    public void startBfd() throws Exception {

        // given
        final String srcSwIp = "10.0.0.1";
        final String srcSwDpid = "00:de:ad:be:ef:00";
        final String dstSwIp = "10.0.0.2";
        final String dstSwDpid = "00:de:ad:be:ef:01";
        final int BFD_INTERVAL = 1;
        final short KEEP_ALIVE_TIMEOUT = 2;
        final short MULTIPLIER = 1;
        final int MYDISC = 10;
        final int PORT_NUM = 5;

        IOFSwitch srcSw = createMock(IOFSwitch.class);
        IOFSwitch dstSw = createMock(IOFSwitch.class);
        OFPort port = createMock(OFPort.class);
        SwitchDescription switchDescription = createMock(SwitchDescription.class);

        expect(srcSw.getInetAddress()).andStubReturn(new InetSocketAddress(srcSwIp, 48321));
        expect(dstSw.getInetAddress()).andStubReturn(new InetSocketAddress(dstSwIp, 48322));
        expect(srcSw.getId()).andStubReturn(DatapathId.of(srcSwDpid));
        expect(dstSw.getId()).andStubReturn(DatapathId.of(dstSwDpid));
        expect(switchDescription.getManufacturerDescription()).andStubReturn("Noviflow");
        expect(srcSw.getSwitchDescription()).andStubReturn(switchDescription);
        expect(ofSwitchService.getSwitch(DatapathId.of(srcSwDpid))).andStubReturn(srcSw);
        expect(ofSwitchService.getSwitch(DatapathId.of(dstSwDpid))).andStubReturn(dstSw);
        expect(srcSw.getOFFactory()).andStubReturn(ofFactory);
        expect(dstSw.getOFFactory()).andStubReturn(ofFactory);
        expect(port.getPortNumber()).andStubReturn(PORT_NUM);

        Capture <OFPacketOut> ofPacketOutCapture = new Capture<>();
        expect(srcSw.write(capture(ofPacketOutCapture))).andReturn(true);

        replay(srcSw, dstSw, port, ofSwitchService, switchDescription);

        // when
        switchManager.startBfd(DatapathId.of(srcSwDpid), DatapathId.of(dstSwDpid), BFD_INTERVAL, KEEP_ALIVE_TIMEOUT,
                MULTIPLIER, MYDISC, port);

        // then
        List<OFAction> actions = ofPacketOutCapture.getValue().getActions();
        assertThat(actions.get(0), instanceOf(OFActionNoviflowBfdStart.class));

        byte[] data = ofPacketOutCapture.getValue().getData();
        IPacket packet = new Ethernet().deserialize(data, 0, data.length);
        Ethernet ethernet = (Ethernet) packet;
        IPv4 ip = (IPv4) ethernet.getPayload();
        UDP udp = (UDP) ip.getPayload();
        assertEquals(ethernet.getSourceMACAddress().toString(), srcSwDpid);
        assertEquals(ethernet.getDestinationMACAddress().toString(), dstSwDpid);
        assertEquals(ethernet.getEtherType(), EthType.IPv4);
        assertEquals(ip.getSourceAddress().toString(), srcSwIp);
        assertEquals(ip.getDestinationAddress().toString(), dstSwIp);
        assertEquals(ip.getProtocol(), IpProtocol.UDP);
        assertEquals(udp.getDestinationPort().getPort(), SwitchManager.UDP_BFD_PORT);
    }

    private void mockBarrierRequest() throws InterruptedException, ExecutionException, TimeoutException {
        OFBarrierReply ofBarrierReply = mock(OFBarrierReply.class);

        ListenableFuture<OFBarrierReply> ofBarrierFuture = mock(ListenableFuture.class);
        expect(ofBarrierFuture.get(anyLong(), anyObject())).andStubReturn(ofBarrierReply);
        replay(ofBarrierFuture);

        expect(iofSwitch.writeRequest(anyObject(OFBarrierRequest.class))).andReturn(ofBarrierFuture);
    }

    private void mockFlowStatsRequest(Long... cookies)
            throws InterruptedException, ExecutionException, TimeoutException {
        List<OFFlowStatsEntry> ofFlowStatsEntries = Arrays.stream(cookies)
                .map(cookie -> {
                    OFFlowStatsEntry ofFlowStatsEntry = mock(OFFlowStatsEntry.class);
                    expect(ofFlowStatsEntry.getCookie()).andStubReturn(U64.of(cookie));
                    replay(ofFlowStatsEntry);
                    return ofFlowStatsEntry;
                })
                .collect(Collectors.toList());

        OFFlowStatsEntry ofFlowStatsEntry = mock(OFFlowStatsEntry.class);
        expect(ofFlowStatsEntry.getCookie()).andStubReturn(U64.of(cookie));
        replay(ofFlowStatsEntry);

        OFFlowStatsReply ofFlowStatsReply = mock(OFFlowStatsReply.class);
        expect(ofFlowStatsReply.getXid()).andReturn(0L);
        expect(ofFlowStatsReply.getFlags()).andReturn(emptySet());
        expect(ofFlowStatsReply.getEntries()).andStubReturn(ofFlowStatsEntries);
        replay(ofFlowStatsReply);

        ListenableFuture<List<OFFlowStatsReply>> ofStatsFuture = mock(ListenableFuture.class);
        expect(ofStatsFuture.get(anyLong(), anyObject())).andStubReturn(singletonList(ofFlowStatsReply));
        replay(ofStatsFuture);

        expect(iofSwitch.writeStatsRequest(anyObject(OFFlowStatsRequest.class))).andReturn(ofStatsFuture);
    }

    private Capture<OFFlowMod> prepareForInstallTest() {
        Capture<OFFlowMod> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(switchDescription.getManufacturerDescription()).andStubReturn("");
        expect(iofSwitch.write(capture(capture))).andReturn(true);
        expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(switchDescription);

        return capture;
    }

    private Capture<OFMeterMod> prepareForMeterTest() {
        Capture<OFMeterMod> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(switchDescription.getManufacturerDescription()).andStubReturn("");
        expect(iofSwitch.write(capture(capture))).andReturn(true);
        expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(switchDescription);

        return capture;
    }
}
