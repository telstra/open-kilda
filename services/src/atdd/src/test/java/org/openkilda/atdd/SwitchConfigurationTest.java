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
import org.openkilda.atdd.utils.controller.ControllerUtils;
import org.openkilda.atdd.utils.controller.PortEntry;
import org.openkilda.atdd.utils.controller.SwitchEntry;
import org.openkilda.messaging.command.switches.PortStatus;
import org.openkilda.northbound.dto.switches.PortDto;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;

import java.util.Optional;

public class SwitchConfigurationTest {

    private final ControllerUtils controllerUtils;
    
    public SwitchConfigurationTest() throws Exception {
        controllerUtils = new ControllerUtils();
    }
    
    @Then("^switch \"([^\"]*)\" with port number as \"([^\"]*)\" status from down to up and verify the state$")
    public void changePortStatusDownToUp(String switchName, int portNumber) throws Throwable {
        PortDto portDto = SwitchesUtils.changeSwitchPortStatus(switchName, portNumber, PortStatus.UP);
        assertTrue(portDto != null);

        Thread.sleep(1000L);
        assertTrue(isPortEnable(switchName, portNumber));
    }

    @And("^switch \"([^\"]*)\" with port number as \"([^\"]*)\" status from up to down and verify the state$")
    public void changePortStatusUpToDown(String switchName, int portNumber) throws Throwable {
        PortDto portDto = SwitchesUtils.changeSwitchPortStatus(switchName, portNumber, PortStatus.DOWN);
        assertTrue(portDto != null);

        Thread.sleep(1000L);
        assertTrue(!isPortEnable(switchName, portNumber));
    }
    
    private boolean isPortEnable(String switchName, int portNumber) throws Exception {
        SwitchEntry switchEntry = controllerUtils.getSwitchPorts(switchName);
        Optional<PortEntry> portEntires = switchEntry.getPortEntries().stream()
                .filter((port) -> port.getPortNumber().equalsIgnoreCase(String.valueOf(portNumber))).findFirst();
        
        if (portEntires.isPresent()) {
            return !portEntires.get().getConfig().stream()
                    .anyMatch((value) -> value.equalsIgnoreCase("PORT_DOWN"));
        } else {
            throw new Exception("Port Not Found");
        }
    }
}
