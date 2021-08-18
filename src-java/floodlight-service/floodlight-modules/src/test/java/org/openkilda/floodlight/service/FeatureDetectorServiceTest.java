/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.service;

import static org.easymock.EasyMock.expect;
import static org.openkilda.model.SwitchFeature.BFD;
import static org.openkilda.model.SwitchFeature.BFD_REVIEW;
import static org.openkilda.model.SwitchFeature.GROUPS;
import static org.openkilda.model.SwitchFeature.GROUP_PACKET_OUT_CONTROLLER;
import static org.openkilda.model.SwitchFeature.HALF_SIZE_METADATA;
import static org.openkilda.model.SwitchFeature.INACCURATE_METER;
import static org.openkilda.model.SwitchFeature.INACCURATE_SET_VLAN_VID_ACTION;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_SWAP_FIELD;
import static org.openkilda.model.SwitchFeature.LAG;
import static org.openkilda.model.SwitchFeature.LIMITED_BURST_SIZE;
import static org.openkilda.model.SwitchFeature.MATCH_UDP_PORT;
import static org.openkilda.model.SwitchFeature.MAX_BURST_COEFFICIENT_LIMITATION;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.MULTI_TABLE;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;

import org.openkilda.model.SwitchFeature;

import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMockSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFVersion;

import java.util.Set;

public class FeatureDetectorServiceTest extends EasyMockSupport {
    private final FeatureDetectorService featuresDetector = new FeatureDetectorService();
    private final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        featuresDetector.setup(moduleContext);
    }

    @After
    public void tearDown() {
        verifyAll();
    }

    @Test
    public void bfdReview() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("NoviFlow Inc")
                        .setHardwareDescription("NS21100")
                        .setSoftwareDescription("NW500.2.10")
                        .setDatapathDescription(
                                "Pure OpenFlow switch from NoviFlow, Hostname: sw0, of1: 127.0.1.1, cli: 127.0.2.1")
                        .build(),
                OFVersion.OF_14, 7);
        discoveryContain(sw, BFD_REVIEW);
    }

    @Test
    public void testOvs() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("Nicira, Inc.")
                        .setHardwareDescription("Open vSwitch")
                        .setSoftwareDescription("2.12.0")
                        .build(),
                OFVersion.OF_13, 255);
        discoveryCheck(sw, ImmutableSet.of(
                GROUP_PACKET_OUT_CONTROLLER, MATCH_UDP_PORT, MULTI_TABLE, PKTPS_FLAG, RESET_COUNTS_FLAG, GROUPS));
    }

    @Test
    public void testKildaOvs() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("Nicira, Inc.")
                        .setHardwareDescription("Open vSwitch")
                        .setSoftwareDescription("2.15.0-kilda")
                        .build(),
                OFVersion.OF_13, 255);
        discoveryCheck(sw, ImmutableSet.of(
                GROUP_PACKET_OUT_CONTROLLER, MATCH_UDP_PORT, MULTI_TABLE, PKTPS_FLAG, RESET_COUNTS_FLAG, GROUPS,
                KILDA_OVS_COPY_FIELD, KILDA_OVS_SWAP_FIELD));
    }

    @Test
    public void testKildaOvs15() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("Nicira, Inc.")
                        .setHardwareDescription("Open vSwitch")
                        .setSoftwareDescription("2.15.0-kilda")
                        .build(),
                OFVersion.OF_15, 255);
        discoveryCheck(sw, ImmutableSet.of(
                GROUP_PACKET_OUT_CONTROLLER, MATCH_UDP_PORT, MULTI_TABLE, PKTPS_FLAG, RESET_COUNTS_FLAG, GROUPS));
    }

    @Test
    public void testSonusSwitch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("Sonus Networks Inc, 4 Technology Park Dr, Westford, MA 01886, USA")
                        .setHardwareDescription("VX3048")
                        .setSoftwareDescription("8.1.0.14")
                        .setDatapathDescription("OpenFlow 1.2 Switch Datapath")
                        .build(),
                OFVersion.OF_12, 1);
        discoveryCheck(sw, ImmutableSet.of(MATCH_UDP_PORT, PKTPS_FLAG));
    }

    @Test
    public void testCentecSwitch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("2004-2015 Centec Networks Inc")
                        .setHardwareDescription("48S")
                        .setSoftwareDescription("2.8.16.15")
                        .setDatapathDescription("ASIC based data plane  ")
                        .build(),
                OFVersion.OF_13, 2);
        discoveryCheck(sw, ImmutableSet.of(
                METERS, INACCURATE_SET_VLAN_VID_ACTION, LIMITED_BURST_SIZE, MULTI_TABLE));
    }

    @Test
    public void testNs21100Switch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("NoviFlow Inc")
                        .setHardwareDescription("NS21100")
                        .setSoftwareDescription("NW500.2.10")
                        .setDatapathDescription(
                                "Pure OpenFlow switch from NoviFlow, Hostname: sw0, of1: 127.0.1.1, cli: 127.0.2.1")
                        .build(),
                OFVersion.OF_13, 7);
        discoveryCheck(sw, ImmutableSet.of(
                BFD, GROUP_PACKET_OUT_CONTROLLER, HALF_SIZE_METADATA, MATCH_UDP_PORT, MAX_BURST_COEFFICIENT_LIMITATION,
                METERS, MULTI_TABLE, NOVIFLOW_COPY_FIELD, NOVIFLOW_PUSH_POP_VXLAN, NOVIFLOW_SWAP_ETH_SRC_ETH_DST, LAG,
                PKTPS_FLAG, RESET_COUNTS_FLAG, GROUPS));
    }

    @Test
    public void testNs2128Switch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("NoviFlow Inc")
                        .setHardwareDescription("NS2128")
                        .setSoftwareDescription("NW500.2.10")
                        .setDatapathDescription(
                                "Pure OpenFlow switch from NoviFlow, Hostname: sw0, of1: 127.0.1.1, cli: 127.0.2.1")
                        .build(),
                OFVersion.OF_13, 7);
        discoveryCheck(sw, ImmutableSet.of(
                BFD, GROUP_PACKET_OUT_CONTROLLER, HALF_SIZE_METADATA, MATCH_UDP_PORT, MAX_BURST_COEFFICIENT_LIMITATION,
                METERS, MULTI_TABLE, NOVIFLOW_COPY_FIELD, NOVIFLOW_PUSH_POP_VXLAN, NOVIFLOW_SWAP_ETH_SRC_ETH_DST, LAG,
                PKTPS_FLAG, RESET_COUNTS_FLAG, GROUPS));
    }

    @Test
    public void testNs2150Switch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("NoviFlow Inc")
                        .setHardwareDescription("NS2150")
                        .setSoftwareDescription("NW500.2.7")
                        .setDatapathDescription(
                                "Pure OpenFlow switch from NoviFlow, Hostname: sw0, of1: 127.0.1.1, cli: 127.0.2.1")
                        .build(),
                OFVersion.OF_13, 7);
        discoveryCheck(sw, ImmutableSet.of(
                BFD, GROUP_PACKET_OUT_CONTROLLER, HALF_SIZE_METADATA, MATCH_UDP_PORT, MAX_BURST_COEFFICIENT_LIMITATION,
                METERS, MULTI_TABLE, NOVIFLOW_COPY_FIELD, NOVIFLOW_PUSH_POP_VXLAN, NOVIFLOW_SWAP_ETH_SRC_ETH_DST, LAG,
                PKTPS_FLAG, RESET_COUNTS_FLAG, GROUPS));
    }

    @Test
    public void testWb5164eV4xxSwitch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("E")
                        .setHardwareDescription("WB5164")
                        .setSoftwareDescription("NW400.4.0")
                        .build(),
                OFVersion.OF_13, 7);
        discoveryCheck(sw,
                ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, BFD, METERS, RESET_COUNTS_FLAG, MATCH_UDP_PORT, LAG,
                        MAX_BURST_COEFFICIENT_LIMITATION, MULTI_TABLE, NOVIFLOW_PUSH_POP_VXLAN, INACCURATE_METER,
                        HALF_SIZE_METADATA, NOVIFLOW_SWAP_ETH_SRC_ETH_DST, GROUPS));
    }

    @Test
    public void testWb5164eV5xxSwitch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("NoviFlow Inc")
                        .setHardwareDescription("WB5164-E")
                        .setSoftwareDescription("NW500.2.10")
                        .setDatapathDescription(
                                "Pure OpenFlow switch from NoviFlow, Hostname: sw0, of1: 127.0.1.1, cli: 127.0.2.1")
                        .build(),
                OFVersion.OF_13, 7);
        discoveryCheck(sw, ImmutableSet.of(
                GROUP_PACKET_OUT_CONTROLLER, BFD, METERS, RESET_COUNTS_FLAG, INACCURATE_METER, MATCH_UDP_PORT, LAG,
                MULTI_TABLE, NOVIFLOW_PUSH_POP_VXLAN, HALF_SIZE_METADATA, NOVIFLOW_SWAP_ETH_SRC_ETH_DST, GROUPS));
    }

    @Test
    public void testNoviflowVirtualSwitch() {
        IOFSwitch sw = makeSwitchMock(SwitchDescription.builder()
                        .setManufacturerDescription("NoviFlow Inc")
                        .setHardwareDescription("SM5000-SM")
                        .setSoftwareDescription("NW500.0.1")
                        .build(),
                OFVersion.OF_13, 2);
        discoveryCheck(sw,
                ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, BFD, METERS, RESET_COUNTS_FLAG, MATCH_UDP_PORT, LAG,
                        MAX_BURST_COEFFICIENT_LIMITATION, MULTI_TABLE, NOVIFLOW_PUSH_POP_VXLAN, HALF_SIZE_METADATA,
                        NOVIFLOW_SWAP_ETH_SRC_ETH_DST, GROUPS));
    }

    private void discoveryCheck(IOFSwitch sw, Set<SwitchFeature> expectedFeatures) {
        replayAll();

        Set<SwitchFeature> actualFeatures = featuresDetector.detectSwitch(sw);
        Assert.assertEquals(expectedFeatures, actualFeatures);
    }

    private void discoveryContain(IOFSwitch sw, SwitchFeature... expectedFeatules) {
        replayAll();

        Set<SwitchFeature> actualFeatures = featuresDetector.detectSwitch(sw);
        for (SwitchFeature expected : expectedFeatules) {
            Assert.assertTrue(actualFeatures.contains(expected));
        }
    }

    private IOFSwitch makeSwitchMock(SwitchDescription description, OFVersion version, int numTables) {
        OFFactory ofFactory = createMock(OFFactory.class);
        expect(ofFactory.getVersion()).andReturn(version).anyTimes();

        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getSwitchDescription()).andReturn(description).anyTimes();
        expect(sw.getOFFactory()).andReturn(ofFactory).anyTimes();
        expect(sw.getNumTables()).andReturn((short) numTables).anyTimes();

        return sw;
    }
}
