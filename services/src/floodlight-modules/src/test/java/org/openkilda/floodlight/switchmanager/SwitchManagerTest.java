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

import static org.openkilda.floodlight.Constants.bandwidth;
import static org.openkilda.floodlight.Constants.burstSize;
import static org.openkilda.floodlight.Constants.inputPort;
import static org.openkilda.floodlight.Constants.inputVlanId;
import static org.openkilda.floodlight.Constants.meterId;
import static org.openkilda.floodlight.Constants.outputPort;
import static org.openkilda.floodlight.Constants.outputVlanId;
import static org.openkilda.floodlight.Constants.transitVlanId;
import static org.openkilda.floodlight.message.command.encapsulation.PushSchemeOutputCommands.ofFactory;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import org.openkilda.floodlight.message.command.encapsulation.OutputCommands;
import org.openkilda.floodlight.message.command.encapsulation.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.types.DatapathId;

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

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(switchDescription);

        switchManager.installMeter(dpid, bandwidth, burstSize, meterId);
    }

    @Test
    public void deleteFlow() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();
        switchManager.deleteFlow(dpid, cookieHex, cookie);
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(Long.valueOf(cookieHex, 16).longValue(), actual.getCookie().getValue());
        assertEquals(SwitchManager.NON_SYSTEM_MASK, actual.getCookieMask());
    }

    @Test
    public void deleteMeter() {
        final Capture<OFMeterMod> capture = prepareForMeterTest();
        switchManager.deleteMeter(dpid, meterId);
        final OFMeterMod meterMod = capture.getValue();
        assertEquals(meterMod.getCommand(), OFMeterModCommand.DELETE);
        assertEquals(meterMod.getMeterId(), meterId);
    }

    private Capture<OFFlowMod> prepareForInstallTest() {
        Capture<OFFlowMod> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);
        expect(switchDescription.getManufacturerDescription()).andStubReturn("");
        expect(iofSwitch.write(capture(capture))).andReturn(true);
        EasyMock.expectLastCall();

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
        EasyMock.expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(switchDescription);

        return capture;
    }
}
