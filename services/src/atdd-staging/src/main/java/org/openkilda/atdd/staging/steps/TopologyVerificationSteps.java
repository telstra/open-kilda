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
package org.openkilda.atdd.staging.steps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cucumber.api.java.en.Given;
import cucumber.api.java8.En;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.steps.helpers.TopologyChecker;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class TopologyVerificationSteps implements En {

    @Autowired
    private TopologyEngineService topologyEngineService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Given("^a reference topology$")
    public void checkTheTopology() {
        List<TopologyDefinition.Switch> referenceSwitches = topologyDefinition.getActiveSwitches();
        List<SwitchInfoData> actualSwitches = topologyEngineService.getActiveSwitches();

        assertFalse("No switches were discovered", actualSwitches.isEmpty());
        assertTrue("Expected and discovered switches are different",
                TopologyChecker.matchSwitches(actualSwitches, referenceSwitches));

        List<TopologyDefinition.Isl> referenceLinks = topologyDefinition.getIslsForActiveSwitches();
        List<IslInfoData> actualLinks = topologyEngineService.getAllLinks();

        assertFalse("No links were discovered", actualLinks.isEmpty());
        assertTrue("Reference links were not discovered / not provided",
                TopologyChecker.matchLinks(actualLinks, referenceLinks));
    }
}
