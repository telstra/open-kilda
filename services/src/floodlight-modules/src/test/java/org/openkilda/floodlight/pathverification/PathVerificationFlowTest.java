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

package org.openkilda.floodlight.pathverification;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import org.openkilda.floodlight.FloodlightTestCase;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PathVerificationFlowTest extends FloodlightTestCase {
    private static Logger logger;
    protected FloodlightContext cntx;
    protected PathVerificationService pvs;
    protected IOFSwitch sw;
    protected OFFeaturesReply swFeatures;
    protected OFDescStatsReply swDescription;
    private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
    private Long swId = 12L;
    private DatapathId swDpid = DatapathId.of(swId);
    private MacAddress targetMac = MacAddress.of(swId);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        logger = LoggerFactory.getLogger(PathVerificationFlowTest.class);
        cntx = new FloodlightContext();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);

        swDescription = factory.buildDescStatsReply().build();
        swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();

        sw = EasyMock.createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(swDpid).anyTimes();
        expect(sw.getOFFactory()).andReturn(factory).anyTimes();
        expect(sw.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();
        expect(sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
        expect(sw.isActive()).andReturn(true).anyTimes();
        expect(sw.getLatency()).andReturn(U64.of(10L)).anyTimes();
        replay(sw);

        pvs = new PathVerificationService();
    }

    @After
    public void tearDown() throws Exception {
    }

//  @SuppressWarnings("static-access")
//  @Test
//  public void testBcastPathVerificationFlow() {
//    Match match = pvs.buildVerificationMatch(sw, true);
//    assertEquals(match.get(MatchField.ETH_DST), MacAddress.of(pvs.VERIFICATION_BCAST_PACKET_DST));
//  }
//
//  @Test
//  public void testDpidToMac() {
//    assertArrayEquals(targetMac.getBytes(), pvs.dpidToMac(sw).getBytes());
//  }
//
//  @Test
//  public void testUnicastPathVerificationFlow() {
//    Match match = pvs.buildVerificationMatch(sw, false);
//    assertEquals(match.get(MatchField.ETH_DST), MacAddress.of(swDpid));
//  }
//
//  @Test
//  public void testPathVerificationAction() {
//    List<OFAction> actions = pvs.buildSendToControllerAction(sw);
//    assertEquals(actions.size(), 2);
//    assertEquals(actions.get(0).getVersion(), OFVersion.OF_13);
//
//    // Should output to controller
//    assertEquals(actions.get(0).getType(), OFActionType.OUTPUT);
//    OFActionOutput sendToControllerRule = (OFActionOutput) actions.get(0);
//    assertEquals(sendToControllerRule.getPort(), OFPort.CONTROLLER);
//
//    // Should rewrite DST_MAC to DPID MAC via OXM
//    assertEquals(actions.get(1).getType(), OFActionType.SET_FIELD);
//    OFActionSetField rewriteMac = (OFActionSetField) actions.get(1);
//    assertEquals(rewriteMac.getField().getValue(), pvs.dpidToMac(sw));
//    logger.info(actions.get(1).toString());
//  }

}
