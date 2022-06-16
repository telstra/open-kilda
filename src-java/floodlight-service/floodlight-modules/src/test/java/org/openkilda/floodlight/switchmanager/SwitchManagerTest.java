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

package org.openkilda.floodlight.switchmanager;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.Every.everyItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.buildMeterMod;
import static org.openkilda.floodlight.test.standard.PushSchemeOutputCommands.ofFactory;
import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;
import static org.openkilda.model.SwitchFeature.BFD;
import static org.openkilda.model.SwitchFeature.GROUP_PACKET_OUT_CONTROLLER;
import static org.openkilda.model.SwitchFeature.LIMITED_BURST_SIZE;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.projectfloodlight.openflow.protocol.OFMeterModCommand.ADD;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.pathverification.PathVerificationServiceConfig;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowFactory;
import org.openkilda.floodlight.test.standard.OutputCommands;
import org.openkilda.floodlight.test.standard.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import org.apache.commons.lang3.StringUtils;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class SwitchManagerTest {
    private static final OutputCommands scheme = new ReplaceSchemeOutputCommands();
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private static final long cookie = 123L;
    private static final SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static final long unicastMeterId = createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE).getValue();
    private SwitchManager switchManager;
    private IOFSwitchService ofSwitchService;
    private FeatureDetectorService featureDetectorService;
    private IOFSwitch iofSwitch;
    private SwitchDescription switchDescription;
    private DatapathId dpid;
    private SwitchManagerConfig config;

    @Before
    public void setUp() throws FloodlightModuleException {
        JdkProxyStaticConfigurationFactory factory = new JdkProxyStaticConfigurationFactory();
        config = factory.createConfiguration(SwitchManagerConfig.class, new MapConfigurationSource(emptyMap()));

        ofSwitchService = createMock(IOFSwitchService.class);
        IRestApiService restApiService = createMock(IRestApiService.class);
        featureDetectorService = createMock(FeatureDetectorService.class);
        SwitchFlowFactory switchFlowFactory = new SwitchFlowFactory();

        iofSwitch = createMock(IOFSwitch.class);

        switchDescription = createMock(SwitchDescription.class);
        dpid = DatapathId.of(SWITCH_ID.toLong());

        PathVerificationServiceConfig config = EasyMock.createMock(PathVerificationServiceConfig.class);
        expect(config.getVerificationBcastPacketDst()).andReturn("00:26:E1:FF:FF:FF").anyTimes();
        replay(config);
        PathVerificationService pathVerificationService = EasyMock.createMock(PathVerificationService.class);
        expect(pathVerificationService.getConfig()).andReturn(config).anyTimes();
        replay(pathVerificationService);

        KildaCore kildaCore = EasyMock.createMock(KildaCore.class);
        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(
                context, KildaCore.class);
        KildaCoreConfig coreConfig = provider.getConfiguration(KildaCoreConfig.class);
        expect(kildaCore.getConfig()).andStubReturn(coreConfig);
        EasyMock.replay(kildaCore);
        context.addService(KildaCore.class, kildaCore);

        SwitchTrackingService switchTracking = createMock(SwitchTrackingService.class);
        switchTracking.setup(context);
        replay(switchTracking);
        context.addService(SwitchTrackingService.class, switchTracking);

        IFloodlightProviderService floodlightProvider = createMock(IFloodlightProviderService.class);
        floodlightProvider.addOFMessageListener(EasyMock.eq(OFType.ERROR), EasyMock.anyObject(SwitchManager.class));
        replay(floodlightProvider);
        context.addService(IFloodlightProviderService.class, floodlightProvider);

        context.addService(IRestApiService.class, restApiService);
        context.addService(IOFSwitchService.class, ofSwitchService);
        context.addService(FeatureDetectorService.class, featureDetectorService);
        context.addService(SwitchFlowFactory.class, switchFlowFactory);
        context.addService(IPathVerificationService.class, pathVerificationService);

        switchManager = new SwitchManager();
        context.addService(ISwitchManager.class, switchManager);
        switchManager.init(context);
        switchManager.startUp(context);
    }

    @Test
    public void installEgressIslVxlanRule() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressIslVxlanRule(dpid, 1);

        OFFlowMod result = capture.getValue();
        assertEquals(scheme.installEgressIslVxlanRule(dpid, 1), result);
    }

    @Test
    public void installTransitIslVxlanRule() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installTransitIslVxlanRule(dpid, 1);

        OFFlowMod result = capture.getValue();
        assertEquals(scheme.installTransitIslVxlanRule(dpid, 1), result);
    }

    @Test
    public void installEgressIslVlanRule() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressIslVlanRule(dpid, 1);

        OFFlowMod result = capture.getValue();
        assertEquals(scheme.installEgressIslVlanRule(dpid, 1), result);
    }

    @Test
    public void dumpFlowTable() {
        // TODO
    }

    @Test
    public void dumpMeters() throws InterruptedException, ExecutionException, TimeoutException,
            SwitchOperationException {
        OFMeterConfig firstMeter = ofFactory.buildMeterConfig().setMeterId(1).build();
        OFMeterConfig secondMeter = ofFactory.buildMeterConfig().setMeterId(2).build();

        ListenableFuture<List<OFMeterConfigStatsReply>> ofStatsFuture = createMock(ListenableFuture.class);
        expect(ofStatsFuture.get(anyLong(), anyObject())).andStubReturn(Lists.newArrayList(
                ofFactory.buildMeterConfigStatsReply().setEntries(Lists.newArrayList(firstMeter)).build(),
                ofFactory.buildMeterConfigStatsReply().setEntries(Lists.newArrayList(secondMeter)).build()));

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(switchDescription.getManufacturerDescription()).andStubReturn("");
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.writeStatsRequest(isA(OFMeterConfigStatsRequest.class))).andStubReturn(ofStatsFuture);

        replay(ofSwitchService, iofSwitch, switchDescription, ofStatsFuture);

        List<OFMeterConfig> meters = switchManager.dumpMeters(dpid);
        assertNotNull(meters);
        assertEquals(2, meters.size());
        assertEquals(Sets.newHashSet(firstMeter, secondMeter), new HashSet<>(meters));
    }

    @Test
    public void dumpMetersTimeoutException() throws SwitchOperationException, InterruptedException, ExecutionException,
            TimeoutException {
        ListenableFuture<List<OFMeterConfigStatsReply>> ofStatsFuture = createMock(ListenableFuture.class);
        expect(ofStatsFuture.get(anyLong(), anyObject())).andThrow(new TimeoutException());
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(switchDescription.getManufacturerDescription()).andStubReturn("");
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.writeStatsRequest(isA(OFMeterConfigStatsRequest.class))).andStubReturn(ofStatsFuture);

        replay(ofSwitchService, iofSwitch, switchDescription, ofStatsFuture);

        List<OFMeterConfig> meters = switchManager.dumpMeters(dpid);
        assertNotNull(meters);
        assertTrue(meters.isEmpty());
    }

    @Test
    public void deleteMeter() throws Exception {
        mockBarrierRequest();
        long meterId = 40;
        final Capture<OFMeterMod> capture = prepareForMeterTest();
        switchManager.deleteMeter(dpid, meterId);
        final OFMeterMod meterMod = capture.getValue();
        assertEquals(meterMod.getCommand(), OFMeterModCommand.DELETE);
        assertEquals(meterMod.getMeterId(), meterId);
    }

    @Test(expected = InvalidMeterIdException.class)
    public void deleteMeterWithInvalidId() throws SwitchOperationException {
        switchManager.deleteMeter(dpid, -1);
    }

    @Test
    public void shouldDeleteRuleByCookie() throws Exception {
        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder().inPort(testInPort)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .egressSwitchId(SWITCH_ID).build();
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
        FlowEncapsulationType flowEncapsulationType = FlowEncapsulationType.TRANSIT_VLAN;
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
        DeleteRulesCriteria criteria = DeleteRulesCriteria.builder().encapsulationId((int) testInVlan)
                .encapsulationType(flowEncapsulationType).build();
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

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
                .encapsulationId((int) testInVlan)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .egressSwitchId(SWITCH_ID)
                .build();
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
    public void shouldDeleteRuleByInPortVlanAndMetadata() throws Exception {
        // given
        final int testInPort = 11;
        final short testInVlan = 101;
        final long metadataValue = 17;
        final long metadataMask = 255;

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
                .metadataValue(metadataValue)
                .metadataMask(metadataMask)
                .encapsulationId((int) testInVlan)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .egressSwitchId(SWITCH_ID)
                .build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(testInPort, actual.getMatch().get(MatchField.IN_PORT).getPortNumber());
        assertEquals(testInVlan, actual.getMatch().get(MatchField.VLAN_VID).getVlan());
        assertEquals(metadataValue, actual.getMatch().getMasked(MatchField.METADATA).getValue().getValue().getValue());
        assertEquals(metadataMask, actual.getMatch().getMasked(MatchField.METADATA).getMask().getValue().getValue());
        assertEquals("any", actual.getOutPort().toString());
        assertEquals(0, actual.getInstructions().size());
        assertEquals(0L, actual.getCookie().getValue());
        assertEquals(0L, actual.getCookieMask().getValue());
        assertThat(deletedRules, containsInAnyOrder(cookie));
    }

    @Test
    public void shouldDeleteRuleByInPortAndVxlanTunnel() throws Exception {
        // given
        final int testInPort = 11;
        final short testInVlan = 101;

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
                .encapsulationId((int) testInVlan)
                .encapsulationType(FlowEncapsulationType.VXLAN)
                .egressSwitchId(SWITCH_ID)
                .build();
        List<Long> deletedRules = switchManager.deleteRulesByCriteria(dpid, criteria);

        // then
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(testInPort, actual.getMatch().get(MatchField.IN_PORT).getPortNumber());
        assertEquals(testInVlan, actual.getMatch().get(MatchField.TUNNEL_ID).getValue());
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

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
                .encapsulationId((int) testInVlan)
                .priority(testPriority)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .egressSwitchId(SWITCH_ID)
                .build();
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

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
    public void shouldInstallMeterWithKbpsFlag() throws Exception {
        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        // define that switch is Centec
        expect(switchDescription.getManufacturerDescription()).andStubReturn("Centec Inc.");
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(Sets.newHashSet(METERS));
        mockGetMetersRequest(Collections.emptyList(), true, 0, 0);
        mockBarrierRequest();

        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(1);

        replay(ofSwitchService, iofSwitch, switchDescription, featureDetectorService);

        // when
        long rate = 100L;
        long burstSize = 1000L;
        Set<OFMeterFlags> flags = ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.STATS, OFMeterFlags.BURST);
        OFMeterMod ofMeterMod = buildMeterMod(iofSwitch.getOFFactory(), rate, burstSize, unicastMeterId, flags, ADD);
        switchManager.processMeter(iofSwitch, ofMeterMod);

        // then
        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(1, actual.size());

        // verify meters creation
        assertThat(actual, everyItem(hasProperty("command", equalTo(ADD))));
        assertThat(actual, everyItem(hasProperty("meterId", equalTo(unicastMeterId))));
        assertThat(actual, everyItem(hasProperty("flags", containsInAnyOrder(flags.toArray()))));
        for (OFMeterMod mod : actual) {
            assertThat(mod.getMeters(), everyItem(hasProperty("rate", is(rate))));
            assertThat(mod.getMeters(), everyItem(hasProperty("burstSize", is(burstSize))));
        }
    }

    @Test
    public void shouldReinstallMeterIfFlagIsIncorrect() throws Exception {
        long expectedRate = config.getUnicastRateLimit();
        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        // define that switch is Centec
        expect(switchDescription.getManufacturerDescription()).andStubReturn("Centec Inc.");
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(Sets.newHashSet(METERS));
        mockBarrierRequest();
        mockGetMetersRequest(Lists.newArrayList(unicastMeterId), false, expectedRate,
                config.getSystemMeterBurstSizeInPackets());

        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(2);

        replay(ofSwitchService, iofSwitch, switchDescription, featureDetectorService);

        // when
        Set<OFMeterFlags> flags = ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.BURST);
        OFMeterMod ofMeterMod = buildMeterMod(iofSwitch.getOFFactory(), expectedRate,
                config.getSystemMeterBurstSizeInPackets(), unicastMeterId, flags, ADD);
        switchManager.processMeter(iofSwitch, ofMeterMod);

        // verify meters installation
        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(2, actual.size());

        // verify meters deletion
        assertThat(actual.get(0), hasProperty("command", equalTo(OFMeterModCommand.DELETE)));
        // verify meter installation
        assertThat(actual.get(1), hasProperty("command", equalTo(ADD)));
        assertThat(actual.get(1), hasProperty("meterId", equalTo(unicastMeterId)));
        assertThat(actual.get(1), hasProperty("flags", containsInAnyOrder(flags.toArray())));
    }

    @Test
    public void shouldRenstallMetersIfRateIsUpdated() throws Exception {
        long unicastMeter = createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE).getValue();
        long originRate = config.getBroadcastRateLimit();
        long updatedRate = config.getBroadcastRateLimit() + 10;

        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(StringUtils.EMPTY);
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(Sets.newHashSet(PKTPS_FLAG));
        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        // 1 meter deletion + 1 meters installation
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(2);

        mockBarrierRequest();
        mockGetMetersRequest(Lists.newArrayList(unicastMeter), true, originRate,
                config.getSystemMeterBurstSizeInPackets());
        replay(ofSwitchService, iofSwitch, switchDescription, featureDetectorService);

        //when
        Set<OFMeterFlags> flags = ImmutableSet.of(OFMeterFlags.PKTPS, OFMeterFlags.STATS, OFMeterFlags.BURST);
        OFMeterMod ofMeterMod = buildMeterMod(iofSwitch.getOFFactory(), updatedRate,
                config.getSystemMeterBurstSizeInPackets(), unicastMeter, flags, ADD);
        switchManager.processMeter(iofSwitch, ofMeterMod);

        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(2, actual.size());

        // verify meters deletion
        assertThat(actual.get(0), hasProperty("command", equalTo(OFMeterModCommand.DELETE)));
        // verify meter installation
        assertThat(actual.get(1), hasProperty("command", equalTo(ADD)));
        assertThat(actual.get(1), hasProperty("meterId", equalTo(unicastMeter)));
        assertThat(actual.get(1), hasProperty("flags", containsInAnyOrder(flags.toArray())));
    }

    @Test
    public void shouldRenstallMetersIfBurstSizeIsUpdated() throws Exception {
        long unicastMeter = createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE).getValue();
        long originBurstSize = config.getSystemMeterBurstSizeInPackets();
        long updatedBurstSize = config.getSystemMeterBurstSizeInPackets() + 10;

        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(StringUtils.EMPTY);
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(Sets.newHashSet(PKTPS_FLAG));
        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        // 1 meter deletion + 1 meters installation
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(2);

        mockBarrierRequest();
        mockGetMetersRequest(Lists.newArrayList(unicastMeter), true, config.getUnicastRateLimit(),
                originBurstSize);
        replay(ofSwitchService, iofSwitch, switchDescription, featureDetectorService);

        //when
        Set<OFMeterFlags> flags = ImmutableSet.of(OFMeterFlags.PKTPS, OFMeterFlags.STATS, OFMeterFlags.BURST);
        OFMeterMod ofMeterMod = buildMeterMod(iofSwitch.getOFFactory(), config.getUnicastRateLimit(),
                updatedBurstSize, unicastMeter, flags, ADD);
        switchManager.processMeter(iofSwitch, ofMeterMod);

        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(2, actual.size());

        // verify meters deletion
        assertThat(actual.get(0), hasProperty("command", equalTo(OFMeterModCommand.DELETE)));
        // verify meter installation
        assertThat(actual.get(1), hasProperty("command", equalTo(ADD)));
        assertThat(actual.get(1), hasProperty("meterId", equalTo(unicastMeter)));
        assertThat(actual.get(1), hasProperty("flags", containsInAnyOrder(flags.toArray())));
    }

    private void mockBarrierRequest() throws InterruptedException, ExecutionException, TimeoutException {
        OFBarrierReply ofBarrierReply = mock(OFBarrierReply.class);

        ListenableFuture<OFBarrierReply> ofBarrierFuture = mock(ListenableFuture.class);
        expect(ofBarrierFuture.get(anyLong(), anyObject())).andStubReturn(ofBarrierReply);
        replay(ofBarrierFuture);

        expect(iofSwitch.writeRequest(anyObject(OFBarrierRequest.class))).andStubReturn(ofBarrierFuture);
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
                .collect(toList());

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

        expect(iofSwitch.writeStatsRequest(isA(OFFlowStatsRequest.class))).andReturn(ofStatsFuture);
    }


    private Capture<OFFlowMod> prepareForInstallTest() {
        return prepareForInstallTest(false);
    }

    private Capture<OFFlowMod> prepareForInstallTest(boolean isCentecSwitch) {
        Capture<OFFlowMod> capture = EasyMock.newCapture();

        Set<SwitchFeature> features;
        if (isCentecSwitch) {
            features = Sets.newHashSet(METERS, LIMITED_BURST_SIZE);
        } else {
            features = Sets.newHashSet(BFD, GROUP_PACKET_OUT_CONTROLLER, NOVIFLOW_PUSH_POP_VXLAN, NOVIFLOW_COPY_FIELD,
                    RESET_COUNTS_FLAG);
        }
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(features);

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(isCentecSwitch ? "Centec" : "");
        expect(iofSwitch.write(capture(capture))).andReturn(true);
        expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(switchDescription);
        replay(featureDetectorService);

        return capture;
    }

    private Capture<OFMeterMod> prepareForMeterTest() {
        Capture<OFMeterMod> capture = EasyMock.newCapture();

        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(Sets.newHashSet(METERS));
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(switchDescription.getManufacturerDescription()).andStubReturn("");
        expect(iofSwitch.write(capture(capture))).andReturn(true);
        expectLastCall();

        replay(ofSwitchService, iofSwitch, switchDescription, featureDetectorService);

        return capture;
    }

    private void mockGetMetersRequest(List<Long> meterIds, boolean supportsPkts, long rate, long burstSize)
            throws Exception {
        List<OFMeterConfig> meterConfigs = new ArrayList<>(meterIds.size());
        for (Long meterId : meterIds) {
            OFMeterBandDrop bandDrop = mock(OFMeterBandDrop.class);
            expect(bandDrop.getRate()).andStubReturn(rate);
            expect(bandDrop.getBurstSize()).andStubReturn(burstSize);

            OFMeterConfig meterConfig = mock(OFMeterConfig.class);
            expect(meterConfig.getEntries()).andStubReturn(Collections.singletonList(bandDrop));
            expect(meterConfig.getMeterId()).andStubReturn(meterId);

            Set<OFMeterFlags> flags = ImmutableSet.of(OFMeterFlags.STATS, OFMeterFlags.BURST,
                    supportsPkts ? OFMeterFlags.PKTPS : OFMeterFlags.KBPS);
            expect(meterConfig.getFlags()).andStubReturn(flags);
            replay(bandDrop, meterConfig);
            meterConfigs.add(meterConfig);
        }

        OFMeterConfigStatsReply statsReply = mock(OFMeterConfigStatsReply.class);
        expect(statsReply.getEntries()).andStubReturn(meterConfigs);

        ListenableFuture<List<OFMeterConfigStatsReply>> ofStatsFuture = mock(ListenableFuture.class);
        expect(ofStatsFuture.get(anyLong(), anyObject())).andStubReturn(Collections.singletonList(statsReply));

        replay(statsReply, ofStatsFuture);
        expect(iofSwitch.writeStatsRequest(isA(OFMeterConfigStatsRequest.class)))
                .andStubReturn(ofStatsFuture);
    }
}
