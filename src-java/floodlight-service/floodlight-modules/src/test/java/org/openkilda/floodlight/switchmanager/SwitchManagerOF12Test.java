package org.openkilda.floodlight.switchmanager;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertFalse;

import org.openkilda.floodlight.OFFactoryVer12Mock;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.pathverification.PathVerificationServiceConfig;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowFactory;
import org.openkilda.floodlight.switchmanager.factory.generator.DropLoopFlowGenerator;

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
import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchManagerOF12Test {
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private static IOFSwitchService switchService = createMock(IOFSwitchService.class);

    private final OFFactory ofFactory = new OFFactoryVer12Mock();
    private final DatapathId switchDpId = DatapathId.of(0xdeadbeaf00000001L);
    private final DatapathId ingressSwitchDpId = DatapathId.of(0xdeadbeaf00000001L);
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

        PathVerificationServiceConfig config = EasyMock.createMock(PathVerificationServiceConfig.class);
        expect(config.getVerificationBcastPacketDst()).andReturn("00:26:E1:FF:FF:FF").anyTimes();
        replay(config);
        PathVerificationService pathVerificationService = EasyMock.createMock(PathVerificationService.class);
        expect(pathVerificationService.getConfig()).andReturn(config).anyTimes();
        SwitchFlowFactory switchFlowFactory = EasyMock.createMock(SwitchFlowFactory.class);
        expect(switchFlowFactory.getDropLoopFlowGenerator())
                .andReturn(DropLoopFlowGenerator.builder().build())
                .anyTimes();
        replay(pathVerificationService, switchFlowFactory);
        context.addService(IPathVerificationService.class, pathVerificationService);
        context.addService(SwitchFlowFactory.class, switchFlowFactory);

        switchManager = new SwitchManager();
        switchManager.init(context);
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
