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
package org.openkilda.atdd.staging.tests.flow_crud;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
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
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.traffexam.OperationalException;
import org.openkilda.atdd.staging.tests.AbstractFlowBasedHook;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;


@Ignore
@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/flow_crud.feature"},
        glue = {"org.openkilda.atdd.staging.tests.flow_crud", "org.openkilda.atdd.staging.steps"})
@ActiveProfiles("mock")
public class FlowCrudOverActiveSwitchesTest {

    public static class FlowCrudOverActiveSwitchesHook extends AbstractFlowBasedHook {

        @Before
        public void prepareMocks() throws IOException {
            setup5SwitchTopology();

            mockFlowInTE("sw1", 10, "sw2", 10, 1, 1);
            mockFlowInTE("sw1", 10, "sw3", 10, 2, 2);

            mockMetersInFL("sw1", 10000, 1, 2);
            mockMetersInFL("sw2", 10000, 1);
            mockMetersInFL("sw3", 10000, 2);

            mockFlowCrudInNorthbound();

            mockTraffExam();

            removedFlows.clear();
        }

        private void setup5SwitchTopology() throws IOException {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            TopologyDefinition topology = mapper.readValue(
                    getClass().getResourceAsStream("/5-switch-test-topology.yaml"), TopologyDefinition.class);

            when(topologyDefinition.getActiveSwitches()).thenReturn(topology.getActiveSwitches());
            List<SwitchInfoData> discoveredSwitches = topology.getActiveSwitches().stream()
                    .map(sw -> new SwitchInfoData(sw.getDpId(), SwitchState.ACTIVATED, "", "", "", ""))
                    .collect(toList());
            when(topologyEngineService.getActiveSwitches()).thenReturn(discoveredSwitches);

            when(topologyDefinition.getIslsForActiveSwitches()).thenReturn(topology.getIslsForActiveSwitches());
            List<IslInfoData> discoveredLinks = topology.getIslsForActiveSwitches().stream()
                    .flatMap(link -> Stream.of(
                            new IslInfoData(0,
                                    asList(new PathNode(link.getSrcSwitch().getDpId(), link.getSrcPort(), 0),
                                            new PathNode(link.getDstSwitch().getDpId(), link.getDstPort(), 1)),
                                    link.getMaxBandwidth(), IslChangeType.DISCOVERED, 0),
                            new IslInfoData(0,
                                    asList(new PathNode(link.getDstSwitch().getDpId(), link.getDstPort(), 0),
                                            new PathNode(link.getSrcSwitch().getDpId(), link.getSrcPort(), 1)),
                                    link.getMaxBandwidth(), IslChangeType.DISCOVERED, 0)
                    ))
                    .collect(toList());
            when(topologyEngineService.getActiveLinks()).thenReturn(discoveredLinks);

            when(topologyDefinition.getActiveTraffGens()).thenReturn(topology.getActiveTraffGens());
        }

        @After
        public void assertsAndVerifyMocks() throws OperationalException {
            verify(northboundService, times(2)).addFlow(any());
            verify(northboundService, times(2)).updateFlow(any(), any());
            verify(northboundService, times(2)).deleteFlow(any());

            // 2 flows * 2 directions * (on create + on update) = 8 times
            verify(traffExamService, times(8)).startExam(any());
            // 2 flows * (on create + on update) = 4 times
            verify(traffExamService, times(4)).waitExam(any());

            assertEquals(2, removedFlows.size());
        }
    }
}
