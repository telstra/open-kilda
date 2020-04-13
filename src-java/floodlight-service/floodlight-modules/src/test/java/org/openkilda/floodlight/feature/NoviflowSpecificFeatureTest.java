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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.floodlight.feature.NoviflowSpecificFeature.isNoviSwitch;
import static org.openkilda.floodlight.feature.NoviflowSpecificFeature.isSmSeries;
import static org.openkilda.floodlight.feature.NoviflowSpecificFeature.isWbSeries;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import org.junit.Test;

public class NoviflowSpecificFeatureTest {

    @Test
    public void testIsNoviSwitch() {
        assertTrue(isNoviSwitch(makeSwitchMock("NoviFlow Inc", "NW400.4.0", "NS21100")));
        assertTrue(isNoviSwitch(makeSwitchMock("NoviFlow Inc", "NW500.2.0_dev", "NS21100")));
        assertTrue(isNoviSwitch(makeSwitchMock("NoviFlow Inc", "NW500.0.1", "WB5164-E")));
        assertTrue(isNoviSwitch(makeSwitchMock("NoviFlow Inc", "NW500.0.1", "SM5000-SM")));
        assertTrue(isNoviSwitch(makeSwitchMock("E", "NW400.4.0", "WB5164")));

        assertFalse(isNoviSwitch(makeSwitchMock("Common Inc", "Soft123", "Hard123")));
        assertFalse(isNoviSwitch(makeSwitchMock("Nicira, Inc.", "Soft123", "Hard123")));
        assertFalse(isNoviSwitch(makeSwitchMock("2004-2016 Centec Networks Inc", "2.8.16.21", "48T")));
        assertFalse(isNoviSwitch(makeSwitchMock("Sonus Networks Inc, 4 Technology Park Dr, Westford, MA 01886, USA",
                "8.1.0.14", "VX3048")));
    }

    @Test
    public void testIsWbSeries() {
        assertTrue(isWbSeries(makeSwitchMock("NoviFlow Inc", "NW500.0.1", "WB5164-E")));
        assertTrue(isWbSeries(makeSwitchMock("E", "NW400.4.0", "WB5164")));

        assertFalse(isWbSeries(makeSwitchMock("NoviFlow Inc", "NW400.4.0", "NS21100")));
        assertFalse(isWbSeries(makeSwitchMock("NoviFlow Inc", "NW500.2.0_dev", "NS21100")));
        assertFalse(isWbSeries(makeSwitchMock("NoviFlow Inc", "NW500.0.1", "SM5000-SM")));

        assertFalse(isWbSeries(makeSwitchMock("Common Inc", "Soft123", "Hard123")));
        assertFalse(isWbSeries(makeSwitchMock("Nicira, Inc.", "Soft123", "Hard123")));
        assertFalse(isWbSeries(makeSwitchMock("2004-2016 Centec Networks Inc", "2.8.16.21", "48T")));
        assertFalse(isWbSeries(makeSwitchMock("Sonus Networks Inc, 4 Technology Park Dr, Westford, MA 01886, USA",
                "8.1.0.14", "VX3048")));
    }

    @Test
    public void testIsSmSeries() {
        assertTrue(isSmSeries(makeSwitchMock("NoviFlow Inc", "NW500.0.1", "SM5000-SM")));

        assertFalse(isSmSeries(makeSwitchMock("NoviFlow Inc", "NW500.0.1", "WB5164-E")));
        assertFalse(isSmSeries(makeSwitchMock("E", "NW400.4.0", "WB5164")));
        assertFalse(isSmSeries(makeSwitchMock("NoviFlow Inc", "NW400.4.0", "NS21100")));
        assertFalse(isSmSeries(makeSwitchMock("NoviFlow Inc", "NW500.2.0_dev", "NS21100")));

        assertFalse(isSmSeries(makeSwitchMock("Common Inc", "Soft123", "Hard123")));
        assertFalse(isSmSeries(makeSwitchMock("Nicira, Inc.", "Soft123", "Hard123")));
        assertFalse(isSmSeries(makeSwitchMock("2004-2016 Centec Networks Inc", "2.8.16.21", "48T")));
        assertFalse(isSmSeries(makeSwitchMock("Sonus Networks Inc, 4 Technology Park Dr, Westford, MA 01886, USA",
                "8.1.0.14", "VX3048")));
    }

    private IOFSwitch makeSwitchMock(String manufacturer, String softwareDescription, String hardwareDescription) {
        SwitchDescription description = createMock(SwitchDescription.class);
        expect(description.getManufacturerDescription()).andReturn(manufacturer).anyTimes();
        expect(description.getSoftwareDescription()).andReturn(softwareDescription).anyTimes();
        expect(description.getHardwareDescription()).andReturn(hardwareDescription).anyTimes();

        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getSwitchDescription()).andReturn(description).anyTimes();

        replay(description, sw);
        return sw;
    }
}
