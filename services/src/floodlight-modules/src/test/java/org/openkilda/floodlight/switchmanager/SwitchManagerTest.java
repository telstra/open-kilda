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
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.Every.everyItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.openkilda.floodlight.Constants.inputPort;
import static org.openkilda.floodlight.Constants.inputVlanId;
import static org.openkilda.floodlight.Constants.meterId;
import static org.openkilda.floodlight.Constants.outputPort;
import static org.openkilda.floodlight.Constants.outputVlanId;
import static org.openkilda.floodlight.Constants.transitVlanId;
import static org.openkilda.floodlight.switchmanager.ISwitchManager.OVS_MANUFACTURER;
import static org.openkilda.floodlight.test.standard.PushSchemeOutputCommands.ofFactory;
import static org.openkilda.model.Cookie.CATCH_BFD_RULE_COOKIE;
import static org.openkilda.model.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE;
import static org.openkilda.model.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.model.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID;
import static org.openkilda.model.MeterId.MIN_SYSTEM_RULE_METER_ID;
import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;

import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.test.standard.OutputCommands;
import org.openkilda.floodlight.test.standard.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.model.Switch.Feature;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
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
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class SwitchManagerTest {
    private static final OutputCommands scheme = new ReplaceSchemeOutputCommands();
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private static final long cookie = 123L;
    private static final long bandwidth = 20000L;
    private static final long smallBandwidth = 100L;
    private static final String cookieHex = "7B";
    private static final SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static final DatapathId defaultDpid = DatapathId.of(1);
    private static final String CENTEC_SWITCH_DESCRIPTION = "Centec";
    private static final String NOWIFLOW_SWITCH_DESCRIPTION = "E OF_13 NW400.6.4";
    private static final double MAX_NOVIFLOW_BURST_COEFFICIENT = 1.005;
    private static final long MAX_CENTEC_SWITCH_BURST_SIZE = 32000L;
    private static final int hugeBandwidth = 400000;
    private static final long unicastMeterId = createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE).getValue();
    private static final long broadcastMeterId =
            createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE).getValue();
    private SwitchManager switchManager;
    private IOFSwitchService ofSwitchService;
    private IRestApiService restApiService;
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
        restApiService = createMock(IRestApiService.class);
        featureDetectorService = createMock(FeatureDetectorService.class);
        iofSwitch = createMock(IOFSwitch.class);

        switchDescription = createMock(SwitchDescription.class);
        dpid = DatapathId.of(SWITCH_ID.toLong());

        context.addService(IRestApiService.class, restApiService);
        context.addService(IOFSwitchService.class, ofSwitchService);
        context.addService(FeatureDetectorService.class, featureDetectorService);

        switchManager = new SwitchManager();
        switchManager.init(context);
    }

    @Test
    public void installDefaultRules() throws Exception {
        Map<Long, Capture<OFFlowMod>> capture = prepareForDefaultRuleInstall();

        switchManager.installDefaultRules(defaultDpid);
        OFFlowMod dropFlow = capture.get(DROP_RULE_COOKIE).getValue();
        OFFlowMod verificationBroadcast = capture.get(VERIFICATION_BROADCAST_RULE_COOKIE).getValue();
        OFFlowMod verificationUnicast = capture.get(VERIFICATION_UNICAST_RULE_COOKIE).getValue();
        OFFlowMod dropLoop = capture.get(DROP_VERIFICATION_LOOP_RULE_COOKIE).getValue();
        OFFlowMod catchRule = capture.get(CATCH_BFD_RULE_COOKIE).getValue();

        assertEquals(scheme.installDropFlowRule(), dropFlow);
        assertEquals(scheme.installVerificationBroadcastRule(defaultDpid), verificationBroadcast);
        assertEquals(scheme.installVerificationUnicastRule(defaultDpid), verificationUnicast);
        assertEquals(scheme.installDropLoopRule(defaultDpid), dropLoop);
        assertEquals(scheme.installBfdCatchRule(defaultDpid), catchRule);
    }

    private Map<Long, Capture<OFFlowMod>> prepareForDefaultRuleInstall() throws Exception {
        ListenableFuture<List<OFMeterConfigStatsReply>> ofStatsFuture = mock(ListenableFuture.class);
        OFMeterConfigStatsReply statsReply = mock(OFMeterConfigStatsReply.class);
        mockGetMetersRequest(ImmutableList.of(meterId), true, 10L);

        Capture<OFFlowMod> captureDropFlow = EasyMock.newCapture();
        Capture<OFFlowMod> captureVerificationBroadcast = EasyMock.newCapture();
        Capture<OFFlowMod> captureVerificationUnicast = EasyMock.newCapture();
        Capture<OFFlowMod> captureDropLoop = EasyMock.newCapture();
        Capture<OFFlowMod> captureBfdCatch = EasyMock.newCapture();

        expect(ofSwitchService.getActiveSwitch(defaultDpid)).andStubReturn(iofSwitch);

        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getId()).andReturn(defaultDpid).times(10);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.writeStatsRequest(anyObject(OFMeterConfigStatsRequest.class))).andStubReturn(ofStatsFuture);
        expect(iofSwitch.write(capture(captureDropFlow))).andReturn(true).times(1);
        expect(iofSwitch.write(capture(captureVerificationBroadcast))).andReturn(true).times(2);
        expect(iofSwitch.write(capture(captureVerificationUnicast))).andReturn(true).times(2);
        expect(iofSwitch.write(capture(captureDropLoop))).andReturn(true).times(1);
        expect(iofSwitch.write(capture(captureBfdCatch))).andReturn(true).times(1);

        expect(iofSwitch.write(anyObject(OFMeterMod.class))).andReturn(true).times(6);
        expect(iofSwitch.writeRequest(anyObject(OFBarrierRequest.class)))
                .andReturn(Futures.immediateFuture(createMock(OFBarrierReply.class))).times(2);

        expect(ofStatsFuture.get(anyLong(), anyObject())).andStubReturn(Collections.singletonList(statsReply));

        expect(switchDescription.getManufacturerDescription()).andReturn("").times(6);
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(Collections.singleton(Feature.BFD));
        expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(ofStatsFuture);
        replay(statsReply);
        replay(switchDescription);
        replay(featureDetectorService);
        return ImmutableMap.of(DROP_RULE_COOKIE, captureDropFlow,
                VERIFICATION_BROADCAST_RULE_COOKIE, captureVerificationBroadcast,
                VERIFICATION_UNICAST_RULE_COOKIE, captureVerificationUnicast,
                DROP_VERIFICATION_LOOP_RULE_COOKIE, captureDropLoop,
                CATCH_BFD_RULE_COOKIE, captureBfdCatch);
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
    public void installIngressFlowWithoutResetCountsFlag() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest(true);

        switchManager.installIngressFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, 0, transitVlanId, OutputVlanType.NONE, meterId);

        final OFFlowMod actual = capture.getValue();
        assertThat(actual.getFlags().isEmpty(), is(true));
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
    public void installOneSwitchFlowNoneActionWithoutResetCountsFlag() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest(true);

        switchManager.installOneSwitchFlow(dpid, cookieHex, cookie,
                inputPort, outputPort, 0, 0, OutputVlanType.NONE, meterId);

        final OFFlowMod actual = capture.getValue();
        assertThat(actual.getFlags().isEmpty(), is(true));
    }

    @Test
    public void dumpFlowTable() {
        // TODO
    }

    @Test
    public void dumpMeters() {
        // TODO
    }

    @Test
    public void installBandwidthMeterForCentecSwitch() throws Exception {
        runInstallMeterTest(bandwidth, (long) (bandwidth * config.getFlowMeterBurstCoefficient()), true, false);
    }

    @Test
    public void installHugeBandwidthMeterForCentecSwitch() throws Exception {
        runInstallMeterTest(hugeBandwidth, MAX_CENTEC_SWITCH_BURST_SIZE, true, false);
    }

    @Test
    public void installBandwidthMeterForNoviFlowSwitch() throws Exception {
        runInstallMeterTest(bandwidth, (long) (bandwidth * MAX_NOVIFLOW_BURST_COEFFICIENT), false, true);
    }

    @Test
    public void installBandwidthMeter() throws Exception {
        runInstallMeterTest(bandwidth, (long) (bandwidth * config.getFlowMeterBurstCoefficient()), false, false);
    }

    @Test
    public void installSmallBandwidthMeter() throws Exception {
        runInstallMeterTest(smallBandwidth, config.getFlowMeterMinBurstSizeInKbits(), false, false);
    }

    private void runInstallMeterTest(long bandwidth, long burstSize, boolean isCentecSwitch, boolean isNoviFlowSwitch)
            throws Exception {
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getId()).andReturn(dpid);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(switchDescription.getManufacturerDescription())
                .andStubReturn(isCentecSwitch ? CENTEC_SWITCH_DESCRIPTION : "");
        expect(switchDescription.getSoftwareDescription())
                .andStubReturn(isNoviFlowSwitch ? NOWIFLOW_SWITCH_DESCRIPTION : "");

        expect(iofSwitch.write(scheme.installMeter(bandwidth, burstSize, meterId))).andReturn(true);
        expect(iofSwitch.writeRequest(anyObject(OFBarrierRequest.class)))
                .andReturn(Futures.immediateFuture(createMock(OFBarrierReply.class)));

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(switchDescription);

        switchManager.installMeterForFlow(dpid, bandwidth, meterId);
    }

    @Test
    public void installFlowMeterWithNegativeIdTest() throws SwitchOperationException {
        runInstallMeterWithInvalidId(-1L);
    }

    @Test
    public void installFlowMeterWithIdForDefaultRuleTest() throws SwitchOperationException {
        for (int id = MIN_SYSTEM_RULE_METER_ID; id < MAX_SYSTEM_RULE_METER_ID; id++) {
            runInstallMeterWithInvalidId(id);
        }
    }

    private void runInstallMeterWithInvalidId(long meterId) throws SwitchOperationException {
        SwitchManager mock = mock(SwitchManager.class);
        mock.installMeterForFlow(dpid, bandwidth, meterId);
        expectLastCall().andThrow(new InvalidMeterIdException(null, null));
    }

    @Test
    public void deleteMeter() throws Exception {
        mockBarrierRequest();
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
    public void shouldDeleteAllNonDefaultRules() throws Exception {
        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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
    public void shouldDeleteDefaultRulesWithoutMeters() throws Exception {
        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(OVS_MANUFACTURER);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE, DROP_VERIFICATION_LOOP_RULE_COOKIE, CATCH_BFD_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(5);

        mockBarrierRequest();
        mockFlowStatsRequest(cookie);
        expectLastCall();

        replay(ofSwitchService, iofSwitch, switchDescription);

        // when
        List<Long> deletedRules = switchManager.deleteDefaultRules(dpid);

        // then
        final List<OFFlowMod> actual = capture.getValues();
        assertEquals(5, actual.size());
        assertThat(actual, everyItem(hasProperty("command", equalTo(OFFlowModCommand.DELETE))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(DROP_RULE_COOKIE)))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(VERIFICATION_BROADCAST_RULE_COOKIE)))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(VERIFICATION_UNICAST_RULE_COOKIE)))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(DROP_VERIFICATION_LOOP_RULE_COOKIE)))));
        assertThat(actual, hasItem(hasProperty("cookie", equalTo(U64.of(CATCH_BFD_RULE_COOKIE)))));
        assertThat(deletedRules, containsInAnyOrder(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE, DROP_VERIFICATION_LOOP_RULE_COOKIE, CATCH_BFD_RULE_COOKIE));
    }

    @Test
    public void shouldDeleteDefaultRulesWithMeters() throws Exception {
        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andReturn(dpid);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(StringUtils.EMPTY);

        mockFlowStatsRequest(cookie, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE, CATCH_BFD_RULE_COOKIE);

        Capture<OFFlowMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(7);

        mockBarrierRequest();
        mockFlowStatsRequest(cookie);
        mockGetMetersRequest(Collections.emptyList(), true, 0);

        replay(ofSwitchService, iofSwitch, switchDescription);

        // when
        List<Long> deletedRules = switchManager.deleteDefaultRules(dpid);

        // then
        assertThat(deletedRules, containsInAnyOrder(DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE, CATCH_BFD_RULE_COOKIE));

        final List<OFFlowMod> actual = capture.getValues();
        assertEquals(7, actual.size());

        // check rules deletion
        List<OFFlowMod> rulesMod = actual.subList(0, 5);
        assertThat(rulesMod, everyItem(hasProperty("command", equalTo(OFFlowModCommand.DELETE))));
        assertThat(rulesMod, hasItem(hasProperty("cookie", equalTo(U64.of(DROP_RULE_COOKIE)))));
        assertThat(rulesMod, hasItem(hasProperty("cookie", equalTo(U64.of(VERIFICATION_BROADCAST_RULE_COOKIE)))));
        assertThat(rulesMod, hasItem(hasProperty("cookie", equalTo(U64.of(VERIFICATION_UNICAST_RULE_COOKIE)))));
        assertThat(rulesMod, hasItem(hasProperty("cookie", equalTo(U64.of(DROP_VERIFICATION_LOOP_RULE_COOKIE)))));
        assertThat(rulesMod, hasItem(hasProperty("cookie", equalTo(U64.of(CATCH_BFD_RULE_COOKIE)))));


        // verify meters deletion
        List<OFFlowMod> metersMod = actual.subList(rulesMod.size(), actual.size());
        assertThat(metersMod, everyItem(hasProperty("command", equalTo(OFMeterModCommand.DELETE))));
        assertThat(metersMod, hasItem(hasProperty("meterId", equalTo(broadcastMeterId))));
        assertThat(metersMod, hasItem(hasProperty("meterId", equalTo(unicastMeterId))));
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
    public void shouldInstallDropLoopRule() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installDropLoopRule(dpid);

        OFFlowMod result = capture.getValue();
        assertEquals(scheme.installDropLoopRule(dpid), result);
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
        mockGetMetersRequest(Collections.emptyList(), true, 0);
        mockBarrierRequest();

        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(1);

        replay(ofSwitchService, iofSwitch, switchDescription);

        // when
        switchManager.installMeterForDefaultRule(iofSwitch, unicastMeterId, 100L, new ArrayList<>());

        // then
        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(1, actual.size());

        // verify meters creation
        assertThat(actual, everyItem(hasProperty("command", equalTo(OFMeterModCommand.ADD))));
        assertThat(actual, everyItem(hasProperty("meterId", equalTo(unicastMeterId))));
        assertThat(actual, everyItem(hasProperty("flags",
                contains(OFMeterFlags.KBPS, OFMeterFlags.STATS, OFMeterFlags.BURST))));
        for (OFMeterMod mod : actual) {
            long expectedBurstSize =
                    config.getSystemMeterBurstSizeInPackets() * config.getDiscoPacketSize() / 1024L;
            assertThat(mod.getMeters(), everyItem(hasProperty("burstSize", is(expectedBurstSize))));
        }
    }

    @Test
    public void shouldInstallMeterWithPktpsFlag() throws Exception {
        long expectedRate = config.getUnicastRateLimit();
        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(StringUtils.EMPTY);
        mockGetMetersRequest(Collections.emptyList(), true, 0);
        mockBarrierRequest();

        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(1);

        replay(ofSwitchService, iofSwitch, switchDescription);

        // when
        switchManager.installMeterForDefaultRule(iofSwitch, unicastMeterId, expectedRate, new ArrayList<>());

        // verify meters installation
        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(1, actual.size());

        // verify meters creation
        assertThat(actual, everyItem(hasProperty("command", equalTo(OFMeterModCommand.ADD))));
        assertThat(actual, everyItem(hasProperty("meterId", equalTo(unicastMeterId))));
        assertThat(actual, everyItem(hasProperty("flags",
                contains(OFMeterFlags.PKTPS, OFMeterFlags.STATS, OFMeterFlags.BURST))));
        for (OFMeterMod mod : actual) {
            assertThat(mod.getMeters(),
                    everyItem(hasProperty("burstSize", is(config.getSystemMeterBurstSizeInPackets()))));
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
        mockBarrierRequest();
        mockGetMetersRequest(Lists.newArrayList(unicastMeterId), false, expectedRate);

        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(2);

        replay(ofSwitchService, iofSwitch, switchDescription);

        // when
        switchManager.installMeterForDefaultRule(iofSwitch, unicastMeterId, expectedRate, new ArrayList<>());

        // verify meters installation
        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(2, actual.size());

        // verify meters deletion
        assertThat(actual.get(0), hasProperty("command", equalTo(OFMeterModCommand.DELETE)));
        // verify meter installation
        assertThat(actual.get(1), hasProperty("command", equalTo(OFMeterModCommand.ADD)));
        assertThat(actual.get(1), hasProperty("meterId", equalTo(unicastMeterId)));
        assertThat(actual.get(1), hasProperty("flags",
                containsInAnyOrder(OFMeterFlags.KBPS, OFMeterFlags.STATS, OFMeterFlags.BURST)));
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
        Capture<OFMeterMod> capture = EasyMock.newCapture(CaptureType.ALL);
        // 1 meter deletion + 1 meters installation
        expect(iofSwitch.write(capture(capture))).andReturn(true).times(2);

        mockBarrierRequest();
        mockGetMetersRequest(Lists.newArrayList(unicastMeter), true, originRate);
        replay(ofSwitchService, iofSwitch, switchDescription);

        switchManager.installMeterForDefaultRule(iofSwitch, unicastMeter, updatedRate, new ArrayList<>());

        final List<OFMeterMod> actual = capture.getValues();
        assertEquals(2, actual.size());

        // verify meters deletion
        assertThat(actual.get(0), hasProperty("command", equalTo(OFMeterModCommand.DELETE)));
        // verify meter installation
        assertThat(actual.get(1), hasProperty("command", equalTo(OFMeterModCommand.ADD)));
        assertThat(actual.get(1), hasProperty("meterId", equalTo(unicastMeter)));
        assertThat(actual.get(1), hasProperty("flags",
                containsInAnyOrder(OFMeterFlags.PKTPS, OFMeterFlags.STATS, OFMeterFlags.BURST)));
    }

    @Test
    public void shouldNotInstallMetersIfAlreadyExists() throws Exception {
        long expectedRate = config.getBroadcastRateLimit();

        // given
        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(iofSwitch.getId()).andStubReturn(dpid);
        expect(switchDescription.getManufacturerDescription()).andStubReturn(StringUtils.EMPTY);
        Capture<OFFlowMod> capture = EasyMock.newCapture();
        expect(iofSwitch.write(capture(capture))).andStubReturn(true);
        expect(featureDetectorService.detectSwitch(iofSwitch)).andReturn(new HashSet<Feature>());
        mockBarrierRequest();
        mockGetMetersRequest(Lists.newArrayList(unicastMeterId, broadcastMeterId), true, expectedRate);
        replay(ofSwitchService, iofSwitch, switchDescription, featureDetectorService);

        switchManager.installDefaultRules(iofSwitch.getId());
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
        return prepareForInstallTest(false);
    }

    private Capture<OFFlowMod> prepareForInstallTest(boolean isCentecSwitch) {
        Capture<OFFlowMod> capture = EasyMock.newCapture();

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

        return capture;
    }

    private Capture<OFMeterMod> prepareForMeterTest() {
        Capture<OFMeterMod> capture = EasyMock.newCapture();

        expect(ofSwitchService.getActiveSwitch(dpid)).andStubReturn(iofSwitch);
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

    private void mockGetMetersRequest(List<Long> meterIds, boolean supportsPkts, long rate) throws Exception {
        List<OFMeterConfig> meterConfigs = new ArrayList<>(meterIds.size());
        for (Long meterId : meterIds) {
            OFMeterBandDrop bandDrop = mock(OFMeterBandDrop.class);
            expect(bandDrop.getRate()).andStubReturn(rate);

            OFMeterConfig meterConfig = mock(OFMeterConfig.class);
            expect(meterConfig.getEntries()).andStubReturn(Collections.singletonList(bandDrop));
            expect(meterConfig.getMeterId()).andStubReturn(meterId);

            Set<OFMeterFlags> flags = ImmutableSet.of(OFMeterFlags.STATS,
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
        expect(iofSwitch.writeStatsRequest(anyObject(OFMeterConfigStatsRequest.class)))
                .andStubReturn(ofStatsFuture);
    }
}
