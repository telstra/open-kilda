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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.SwitchesUtils;
import org.openkilda.atdd.utils.controller.ControllerUtils;
import org.openkilda.atdd.utils.controller.PortEntry;
import org.openkilda.atdd.utils.controller.SwitchEntry;
import org.openkilda.messaging.command.switches.PortStatus;
import org.openkilda.northbound.dto.switches.PortDto;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.util.Optional;

public class SwitchConfigurationTest {

    private final ControllerUtils controllerUtils;

    public SwitchConfigurationTest() throws Exception {
        controllerUtils = new ControllerUtils();
    }

    @When("^change port status to '(.*)' for switch \"([^\"]*)\" port \"([^\"]*)\"$")
    public void changePortStatus(String switchName, int portNumber, String portStatus) {
        PortDto portDto = SwitchesUtils.changeSwitchPortStatus(switchName, portNumber,
                PortStatus.valueOf(portStatus.toUpperCase()));
        assertTrue(portDto.getSuccess());
    }

    @Then("port status for switch \"(.*)\" port \"(\\d+)\" is '(.*)'")
    public void checkPortStatus(String switchName, int portNumber, String portStatus) throws Exception {
        assertEquals(isPortEnable(switchName, portNumber),
                (PortStatus.valueOf(portStatus.toUpperCase()) == PortStatus.UP));
    }

    private boolean isPortEnable(String switchName, int portNumber) throws Exception {
        SwitchEntry switchEntry = controllerUtils.getSwitchPorts(switchName);
        Optional<PortEntry> portEntires = switchEntry.getPortEntries().stream()
                .filter((port) -> port.getPortNumber().equalsIgnoreCase(String.valueOf(portNumber))).findFirst();

        if (portEntires.isPresent()) {
            return portEntires.get().getConfig().stream()
                    .noneMatch((value) -> value.equalsIgnoreCase("PORT_DOWN"));
        } else {
            throw new Exception("Port Not Found");
        }
    }
}
