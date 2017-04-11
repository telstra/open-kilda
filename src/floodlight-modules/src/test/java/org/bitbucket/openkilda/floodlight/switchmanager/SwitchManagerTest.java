package org.bitbucket.openkilda.floodlight.switchmanager;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import org.easymock.*;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.*;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.bitbucket.openkilda.floodlight.message.command.CommandUtils.*;
import static org.bitbucket.openkilda.floodlight.switchmanager.SwitchManager.cookieMaker;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class SwitchManagerTest {
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private SwitchManager switchManager;
    private IStaticEntryPusherService staticEntryPusher;
    private IOFSwitchService ofSwitchService;
    private IRestApiService restApiService;
    private IOFSwitch iofSwitch;
    private DatapathId dpid;

    @Before
    public void setUp() throws FloodlightModuleException {
        staticEntryPusher = createNiceMock(IStaticEntryPusherService.class);
        ofSwitchService = createMock(IOFSwitchService.class);
        restApiService = createMock(IRestApiService.class);
        iofSwitch = createMock(IOFSwitch.class);
        dpid = createMock(DatapathId.class);

        context.addService(IRestApiService.class, restApiService);
        context.addService(IOFSwitchService.class, ofSwitchService);
        context.addService(IStaticEntryPusherService.class, staticEntryPusher);

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

        switchManager.installIngressFlow(dpid, inputPort, outputPort, inputVlanId, transitVlanId, meterId);

        verify(staticEntryPusher);

        assertEquals(
                ingressReplaceFlowMod(inputPort, outputPort, inputVlanId, transitVlanId, meterId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installIngressFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, inputPort, outputPort, inputVlanId, transitVlanId, meterId);

        verify(staticEntryPusher);

        assertEquals(
                ingressPopFlowMod(inputPort, outputPort, inputVlanId, transitVlanId, meterId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installIngressFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, inputPort, outputPort, 0, transitVlanId, meterId);

        verify(staticEntryPusher);

        assertEquals(
                ingressPushFlowMod(inputPort, outputPort, transitVlanId, meterId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installIngressFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, inputPort, outputPort, 0, transitVlanId, meterId);

        verify(staticEntryPusher);

        assertEquals(
                ingressNoneFlowMod(inputPort, outputPort, transitVlanId, meterId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installEgressFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, inputPort, outputPort, transitVlanId, 0, OutputVlanType.NONE);

        verify(staticEntryPusher);

        assertEquals(
                egressNoneFlowMod(inputPort, outputPort, transitVlanId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installEgressFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, inputPort, outputPort, transitVlanId, outputVlanId, OutputVlanType.PUSH);

        verify(staticEntryPusher);

        assertEquals(
                egressPushFlowMod(inputPort, outputPort, transitVlanId, outputVlanId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installEgressFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, inputPort, outputPort, transitVlanId, 0, OutputVlanType.POP);

        verify(staticEntryPusher);

        assertEquals(
                egressPopFlowMod(inputPort, outputPort, transitVlanId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installEgressFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, inputPort, outputPort, transitVlanId, outputVlanId, OutputVlanType.REPLACE);

        verify(staticEntryPusher);

        assertEquals(
                egressReplaceFlowMod(inputPort, outputPort, transitVlanId, outputVlanId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installTransitFlow() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installTransitFlow(dpid, inputPort, outputPort, transitVlanId);

        verify(staticEntryPusher);

        assertEquals(
                transitFlowMod(inputPort, outputPort, transitVlanId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, inputPort, outputPort,
                inputVlanId, outputVlanId, OutputVlanType.REPLACE, meterId);

        verify(staticEntryPusher);

        assertEquals(
                oneSwitchReplaceFlowMod(inputPort, outputPort, inputVlanId, outputVlanId, meterId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, inputPort, outputPort,0, outputVlanId, OutputVlanType.PUSH, meterId);

        verify(staticEntryPusher);

        assertEquals(
                oneSwitchPushFlowMod(inputPort, outputPort, outputVlanId, meterId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, inputPort, outputPort,inputVlanId, 0, OutputVlanType.POP, meterId);

        verify(staticEntryPusher);

        assertEquals(
                oneSwitchPopFlowMod(inputPort, outputPort, inputVlanId, meterId, cookieMaker()),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, inputPort, outputPort, 0, 0, OutputVlanType.NONE, meterId);

        verify(staticEntryPusher);

        assertEquals(
                oneSwitchNoneFlowMod(inputPort, outputPort, meterId, cookieMaker()),
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
    public void installMeter() throws Exception {
        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        expect(iofSwitch.write(expectedMeter(bandwidth, burstSize, meterId))).andReturn(true);

        replay(ofSwitchService);
        replay(iofSwitch);

        switchManager.installMeter(dpid, bandwidth, burstSize, meterId);
    }

    private Capture<OFFlowMod> prepareForInstallTest() {
        Capture<OFFlowMod> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        staticEntryPusher.addFlow(anyString(), capture(capture), anyObject(DatapathId.class));
        EasyMock.expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);
        EasyMock.replay(staticEntryPusher);

        return capture;
    }
}
