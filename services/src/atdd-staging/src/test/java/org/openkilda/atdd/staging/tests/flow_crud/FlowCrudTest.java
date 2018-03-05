package org.openkilda.atdd.staging.tests.flow_crud;

import static java.util.stream.Collectors.toList;
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
import org.openkilda.atdd.staging.service.FloodlightService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;


@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/flow_crud.feature"},
        glue = {"org.openkilda.atdd.staging.tests.flow_crud", "org.openkilda.atdd.staging.steps"})
@ActiveProfiles("mock")
public class FlowCrudTest {

    public static class DiscoveryMechanismHook {

        @Autowired
        private FloodlightService floodlightService;

        @Autowired
        private TopologyEngineService topologyEngineService;

        @Autowired
        private TopologyDefinition topologyDefinition;

        @Before
        public void prepareMocks() throws IOException {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            TopologyDefinition topology = mapper.readValue(
                    getClass().getResourceAsStream("/3-switch-test-topology.yaml"), TopologyDefinition.class);

            when(topologyDefinition.getActiveSwitches()).thenReturn(topology.getActiveSwitches());

            List<SwitchInfoData> discoveredSwitches = topology.getActiveSwitches().stream()
                    .map(sw -> new SwitchInfoData(sw.getDpId(), SwitchState.ACTIVATED, "", "", "", ""))
                    .collect(toList());
            when(topologyEngineService.getActiveSwitches()).thenReturn(discoveredSwitches);

        }

        @After
        public void verifyMocks() {
        }
    }
}
