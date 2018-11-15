package org.openkilda.floodlight.switchmanager;

import static java.util.Collections.singletonList;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.openkilda.floodlight.switchmanager.SwitchManager.DEFAULT_RULE_PRIORITY;

import org.openkilda.floodlight.OFFactoryVer12Mock;
import org.openkilda.floodlight.error.SwitchOperationException;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

public class SwitchManagerOF12Test {
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private static IOFSwitchService switchService = createMock(IOFSwitchService.class);

    private final OFFactory ofFactory = new OFFactoryVer12Mock();
    private final DatapathId switchDpId = DatapathId.of(0xdeadbeaf00000001L);
    private final long commonFlowCookie = 0x77AA55AA0001L;
    private IOFSwitch ofSwitch = createMock(IOFSwitch.class);

    private SwitchManager switchManager;

    @BeforeClass
    public static void setUpClass() {
        context.addService(IOFSwitchService.class, switchService);
    }

    @Before
    public void setUp() throws FloodlightModuleException {
        EasyMock.reset(switchService);

        switchManager = new SwitchManager();
        switchManager.init(context);
    }

    @Test
    public void installTransitFlow() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallFlowOperation();

        String flowId = "test-transit-flow-rule";
        int inputPort = 2;
        int outputPort = 4;
        int transitVlanId = 512;
        switchManager.installTransitFlow(switchDpId, flowId, commonFlowCookie, inputPort, outputPort, transitVlanId);

        OFFactory referenceOfFactory = new OFFactoryVer12Mock();
        OFFlowMod expected = referenceOfFactory.buildFlowAdd()
                .setCookie(U64.of(commonFlowCookie).applyMask(U64.of(SwitchManager.FLOW_COOKIE_MASK)))
                .setPriority(DEFAULT_RULE_PRIORITY)
                .setMatch(referenceOfFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(transitVlanId), OFVlanVidMatch.ofRawVid(
                                (short) 0x0FFF))
                        .build())
                .setInstructions(singletonList(
                        referenceOfFactory.instructions().applyActions(singletonList(
                                referenceOfFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .build();

        assertEquals(expected, capture.getValue());
    }

    @Test
    public void shouldNotInstallDropLoopRule() throws SwitchOperationException {
        Capture<OFFlowMod> capture = prepareForInstallFlowOperation();

        switchManager.installDropLoopRule(switchDpId);

        // we shouldn't installed anything because of OF version
        assertFalse(capture.hasCaptured());
    }

    private Capture<OFFlowMod> prepareForInstallFlowOperation() {
        Capture<OFFlowMod> capture = EasyMock.newCapture();

        expect(switchService.getActiveSwitch(switchDpId)).andStubReturn(ofSwitch);
        expect(ofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(ofSwitch.write(capture(capture))).andReturn(true);
        EasyMock.expectLastCall();

        EasyMock.replay(switchService);
        EasyMock.replay(ofSwitch);

        return capture;
    }
}
