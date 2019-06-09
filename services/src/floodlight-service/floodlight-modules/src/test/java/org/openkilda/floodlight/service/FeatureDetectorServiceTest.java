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
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.BFD;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.BFD_REVIEW;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.GROUP_PACKET_OUT_CONTROLLER;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.LIMITED_BURST_SIZE;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.METERS;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.RESET_COUNTS_FLAG;

import org.openkilda.messaging.model.SpeakerSwitchView.Feature;

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
    public void metersCommon() {
        discoveryCheck(makeSwitchMock("Common Inc", "Soft123", OFVersion.OF_13),
                       ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, METERS, RESET_COUNTS_FLAG));
    }

    @Test
    public void metersOf12() {
        discoveryCheck(makeSwitchMock("Common Inc", "Soft123", OFVersion.OF_12),
                       ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, RESET_COUNTS_FLAG));
    }

    @Test
    public void metersNicira() {
        discoveryCheck(makeSwitchMock("Nicira, Inc.", "Soft123", OFVersion.OF_13),
                       ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, RESET_COUNTS_FLAG));
    }

    @Test
    public void bfdCommon() {
        discoveryCheck(makeSwitchMock("NoviFlow Inc", "NW400.4.0", OFVersion.OF_13),
                       ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, BFD, METERS, RESET_COUNTS_FLAG,
                               NOVIFLOW_COPY_FIELD));
    }

    @Test
    public void bfdReview() {
        discoveryCheck(makeSwitchMock("NoviFlow Inc", "NW400.4.0", OFVersion.OF_14),
                       ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, BFD, BFD_REVIEW, METERS, RESET_COUNTS_FLAG,
                               NOVIFLOW_COPY_FIELD));
    }


    @Test
    public void copyFieldOnESwitches() {
        discoveryCheck(makeSwitchMock("E", "NW400.4.0", OFVersion.OF_13),
                ImmutableSet.of(GROUP_PACKET_OUT_CONTROLLER, BFD, METERS, RESET_COUNTS_FLAG));
    }

    @Test
    public void roundTripCentec() {
        discoveryCheck(makeSwitchMock("2004-2016 Centec Networks Inc", "2.8.16.21", OFVersion.OF_13),
                       ImmutableSet.of(METERS, LIMITED_BURST_SIZE));
    }

    @Test
    public void roundTripActon() {
        discoveryCheck(makeSwitchMock("Sonus Networks Inc, 4 Technology Park Dr, Westford, MA 01886, USA",
                "8.1.0.14", OFVersion.OF_12),
                ImmutableSet.of(RESET_COUNTS_FLAG));
    }

    private void discoveryCheck(IOFSwitch sw, Set<Feature> expectedFeatures) {
        replayAll();

        Set<Feature> actualFeatures = featuresDetector.detectSwitch(sw);
        Assert.assertEquals(expectedFeatures, actualFeatures);
    }

    private IOFSwitch makeSwitchMock(String manufacturer, String softwareDescription, OFVersion version) {
        SwitchDescription description = createMock(SwitchDescription.class);
        expect(description.getManufacturerDescription()).andReturn(manufacturer).anyTimes();
        expect(description.getSoftwareDescription()).andReturn(softwareDescription).anyTimes();

        OFFactory ofFactory = createMock(OFFactory.class);
        expect(ofFactory.getVersion()).andReturn(version).anyTimes();

        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getSwitchDescription()).andReturn(description).anyTimes();
        expect(sw.getOFFactory()).andReturn(ofFactory).anyTimes();

        return sw;
    }
}
