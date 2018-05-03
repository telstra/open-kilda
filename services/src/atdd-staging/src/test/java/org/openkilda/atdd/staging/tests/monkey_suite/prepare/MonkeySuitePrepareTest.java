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
package org.openkilda.atdd.staging.tests.monkey_suite.prepare;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import org.junit.runner.RunWith;
import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.traffexam.OperationalException;
import org.openkilda.atdd.staging.tests.AbstractFlowBasedHook;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;


@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/monkey_suite.feature"},
        glue = {"org.openkilda.atdd.staging.tests.monkey_suite.prepare", "org.openkilda.atdd.staging.steps"},
        tags = {"@Prepare"})
@ActiveProfiles("mock")
public class MonkeySuitePrepareTest {

    public static class MonkeySuitePrepareHook extends AbstractFlowBasedHook {

        @Before
        public void prepareMocks() throws IOException {
            setup3TraffGensTopology();

            mockFlowInTE("sw1", 10, "sw2", 10, 1, 1);
            mockFlowInTE("sw1", 10, "sw3", 10, 2, 1);
            mockFlowInTE("sw2", 10, "sw3", 10, 3, 1);

            mockFlowCrudInNorthbound();

            mockTraffExam();

            removedFlows.clear();
        }

        private void setup3TraffGensTopology() throws IOException {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            TopologyDefinition topology = mapper.readValue(
                    getClass().getResourceAsStream("/5-switch-test-topology.yaml"), TopologyDefinition.class);

            when(topologyDefinition.getActiveSwitches()).thenReturn(topology.getActiveSwitches());
            when(topologyDefinition.getActiveTraffGens()).thenReturn(topology.getActiveTraffGens());
        }

        @After
        public void assertsAndVerifyMocks() throws OperationalException {
            verify(northboundService, times(3)).addFlow(any());
        }
    }
}
