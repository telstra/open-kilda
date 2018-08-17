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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.SwitchesUtils;
import org.openkilda.atdd.utils.controller.ControllerUtils;
import org.openkilda.atdd.utils.controller.FloodlightQueryException;
import org.openkilda.atdd.utils.controller.PortEntry;
import org.openkilda.messaging.command.switches.PortStatus;
import org.openkilda.northbound.dto.switches.PortDto;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.util.HashMap;
import java.util.List;

public class SwitchConfigurationTest {

    private final ControllerUtils controllerUtils;

    public SwitchConfigurationTest() throws Exception {
        controllerUtils = new ControllerUtils();
    }

    @When("^change port status to '(.*)' for switch \"([^\"]*)\" port \"([^\"]*)\"$")
    public void changePortStatus(String portStatus, String switchName, int portNumber) {
        PortDto portDto = SwitchesUtils.changeSwitchPortStatus(switchName, portNumber,
                PortStatus.valueOf(portStatus.toUpperCase()));
        assertNotNull(portDto);
    }

    @Then("port status for switch \"(.*)\" port \"(\\d+)\" is '(.*)'")
    public void checkPortStatus(String switchName, int portNumber, String portStatus) throws Exception {
        assertEquals(getPortStatuses(switchName).get(portNumber), PortStatus.valueOf(portStatus.toUpperCase()));
    }

    private HashMap<Integer, PortStatus> getPortStatuses(String switchName) throws FloodlightQueryException {
        List<PortEntry> portEntries = controllerUtils.getSwitchPorts(switchName).getPortEntries();
        HashMap<Integer, PortStatus> results = new HashMap<>();
        portEntries.forEach(portEntry -> {
            if (!portEntry.getPortNumber().equals("local")) {
                Integer portNumber = Integer.parseInt(portEntry.getPortNumber());
                PortStatus status = portEntry.getConfig().contains("PORT_DOWN") ? PortStatus.DOWN : PortStatus.UP;
                results.put(portNumber, status);
            }
        });
        return results;
    }

    @And("^all port statuses for switch \"([^\"]*)\" are '(.*)'$")
    public void allPortStatusesForSwitchAreUp(String switchName, String portStatus) throws FloodlightQueryException {
        assertTrue(getPortStatuses(switchName).values().stream()
                .allMatch(status -> status.equals(PortStatus.valueOf(portStatus.toUpperCase()))));
    }

    @And("^all port statuses for switch \"([^\"]*)\" except for port \"([^\"]*)\" are '(.*)'$")
    public void allPortStatusesForSwitchExceptForPortAreUp(String switchName, int portNumber, String portStatus)
            throws Throwable {
        assertTrue(getPortStatuses(switchName).entrySet().stream().filter(entry -> entry.getKey() != portNumber)
                .allMatch(entry -> entry.getValue().equals(PortStatus.valueOf(portStatus.toUpperCase()))));
    }
}
