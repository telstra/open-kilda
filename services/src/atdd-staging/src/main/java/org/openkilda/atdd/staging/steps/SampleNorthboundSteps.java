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
import static org.junit.Assert.assertNotNull;

import cucumber.api.java8.En;
import org.openkilda.atdd.staging.service.NorthboundService;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.topo.ITopology;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class SampleNorthboundSteps implements En {

    @Autowired
    private NorthboundService northboundService;
    @Autowired
    private ITopology topology;

    private List<FlowPayload> result;

    public SampleNorthboundSteps() {
        When("^get flows from Northbound$", () -> {
            result = northboundService.getFlowDump();
        });

        Then("^received flows$", () -> {
            assertNotNull(topology);

            assertFalse("flows are empty", result.isEmpty());
        });
    }
}
