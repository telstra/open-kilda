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

package org.openkilda.atdd;

import static org.junit.Assert.assertTrue;

import org.openkilda.SwitchesUtils;
import org.openkilda.northbound.dto.switches.PortDto;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;

public class SwitchConfigurationTest {
    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";
    
    @Then("^switch \"([^\"]*)\" with port name as \"([^\"]*)\" status from down to up and verify the state$")
    public void changePortStatusDownToUp(String switchName, String portName) throws Throwable {
        try {
            PortDto portDto = SwitchesUtils.changeSwitchPortStatus(switchName, portName, STATUS_UP);
            assertTrue(portDto != null);
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @And("^switch \"([^\"]*)\" with port name as \"([^\"]*)\" status from up to down and verify the state$")
    public void changePortStatusUpToDown(String switchName, String portName) throws Throwable {
        try {
            PortDto portDto = SwitchesUtils.changeSwitchPortStatus(switchName, portName, STATUS_DOWN);
            assertTrue(portDto != null);
        } catch (Exception e) {
            assertTrue(false);
        }
    }
}
