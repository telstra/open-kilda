/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.feature;

import static java.util.Optional.empty;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_15;

import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFVersion;

import java.util.Optional;

public class KildaOvsPushPopMatchVxlanFeatureTest {

    private static final KildaOvsPushPopMatchVxlanFeature FEATURE = new KildaOvsPushPopMatchVxlanFeature();

    @Test
    public void kildaOvsVxlanTest() {
        assertEquals(empty(), FEATURE.discover(makeSwitchMock("NoviFlow Inc", "NW400.4.0", "NS21100", OF_13)));
        assertEquals(empty(), FEATURE.discover(makeSwitchMock("Nicira, Inc.", "2.15.1-kilda", "Open vSwitch", OF_13)));
        assertEquals(empty(), FEATURE.discover(makeSwitchMock("Nicira, Inc.", "2.15.0-kilda", "Open vSwitch", OF_13)));
        assertEquals(empty(), FEATURE.discover(makeSwitchMock("Nicira, Inc.", "2.14.3-kilda", "Open vSwitch", OF_13)));
        assertEquals(empty(), FEATURE
                .discover(makeSwitchMock("Nicira, Inc.", "2.15.1.1-kilda", "Open vSwitch", OF_15)));

        Optional<SwitchFeature> optional = Optional.of(KILDA_OVS_PUSH_POP_MATCH_VXLAN);
        assertEquals(optional, FEATURE.discover(makeSwitchMock("2.15.1.3-kilda")));
        assertEquals(optional, FEATURE.discover(makeSwitchMock("2.15.1.4-kilda")));
        assertEquals(optional, FEATURE.discover(makeSwitchMock("2.15.2-kilda")));
        assertEquals(optional, FEATURE.discover(makeSwitchMock("3.0.0-kilda")));
    }

    private IOFSwitch makeSwitchMock(String softwareDescription) {
        return makeSwitchMock("Nicira, Inc.", softwareDescription, "Open vSwitch", OF_13);
    }

    private IOFSwitch makeSwitchMock(String manufacturer, String softwareDescription, String hardwareDescription,
                                     OFVersion ofVersion) {
        SwitchDescription description = createMock(SwitchDescription.class);
        expect(description.getManufacturerDescription()).andReturn(manufacturer).anyTimes();
        expect(description.getSoftwareDescription()).andReturn(softwareDescription).anyTimes();
        expect(description.getHardwareDescription()).andReturn(hardwareDescription).anyTimes();

        OFFactory factory = createMock(OFFactory.class);
        expect(factory.getVersion()).andReturn(ofVersion);

        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getSwitchDescription()).andReturn(description).anyTimes();
        expect(sw.getOFFactory()).andReturn(factory);

        replay(description, factory, sw);
        return sw;
    }
}
