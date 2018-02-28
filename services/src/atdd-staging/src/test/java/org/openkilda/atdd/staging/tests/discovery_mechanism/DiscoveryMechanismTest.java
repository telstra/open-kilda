package org.openkilda.atdd.staging.tests.discovery_mechanism;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import org.junit.runner.RunWith;
import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Status;
import org.openkilda.atdd.staging.service.FloodlightService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/discovery_mechanism.feature"},
        glue = {"org.openkilda.atdd.staging.tests.discovery_mechanism", "org.openkilda.atdd.staging.steps"})
@ActiveProfiles("mock")
public class DiscoveryMechanismTest {

    public static class DiscoveryMechanismHook {
        @Autowired
        private FloodlightService floodlightService;

        @Autowired
        private TopologyEngineService topologyEngineService;

        @Autowired
        private TopologyDefinition topologyDefinition;

        private final List<SwitchInfoData> discoveredSwitches = ImmutableList.of(
                new SwitchInfoData("00:00:00:00:00:00:00:01", SwitchState.ACTIVATED, EMPTY, EMPTY, EMPTY, EMPTY),
                new SwitchInfoData("00:00:00:00:00:00:00:02", SwitchState.ACTIVATED, EMPTY, EMPTY, EMPTY, EMPTY)
        );

        private final List<PathNode> path = ImmutableList.of(
                new PathNode(discoveredSwitches.get(0).getSwitchId(),0, 0),
                new PathNode(discoveredSwitches.get(1).getSwitchId(),0, 0)
        );

        private List<IslInfoData> discoveredLinks;

        @Before
        public void prepareMocks() {
            List<TopologyDefinition.Switch> switches = discoveredSwitches.stream()
                    .map(this::buildSwitchDefinition)
                    .collect(Collectors.toList());
            when(topologyDefinition.getActiveSwitches()).thenReturn(switches);

            List<TopologyDefinition.Isl> links = buildIslDefinitions(switches);
            when(topologyDefinition.getIslsForActiveSwitches()).thenReturn(links);

            //build forward and reverse isls as discovered links
            List<PathNode> reversedPath = new ArrayList<>(path);
            Collections.reverse(reversedPath);
            discoveredLinks = ImmutableList.of(
                    new IslInfoData(0L, path, 0L, IslChangeType.DISCOVERED, 0L),
                    new IslInfoData(0L, reversedPath, 0L, IslChangeType.DISCOVERED, 0L)
            );
            when(topologyEngineService.getAllSwitches()).thenReturn(discoveredSwitches);
            when(topologyEngineService.getAllLinks()).thenReturn(discoveredLinks);
        }

        @After
        public void verifyMocks() {
            verify(topologyEngineService).getAllSwitches();
            verify(topologyEngineService).getAllLinks();
            verify(topologyDefinition, times(2)).getActiveSwitches();
            verify(topologyDefinition).getIslsForActiveSwitches();
        }

        private TopologyDefinition.Switch buildSwitchDefinition(SwitchInfoData sw) {
            TopologyDefinition.Switch result = new TopologyDefinition.Switch();
            result.setDpId(sw.getSwitchId());
            result.setStatus(Status.Active);
            return result;
        }

        private List<TopologyDefinition.Isl> buildIslDefinitions(List<TopologyDefinition.Switch> switches) {
            TopologyDefinition.Isl result = new TopologyDefinition.Isl();
            result.setSrcSwitch(getSwitchById(path.get(0).getSwitchId(), switches));
            result.setSrcPort(path.get(0).getPortNo());
            result.setDstSwitch(getSwitchById(path.get(1).getSwitchId(), switches));
            result.setDstPort(path.get(1).getPortNo());
            return Collections.singletonList(result);
        }

        private TopologyDefinition.Switch getSwitchById(String switchId, List<TopologyDefinition.Switch> switches) {
            return switches.stream()
                    .filter(sw -> switchId.equals(sw.getDpId()))
                    .findFirst()
                    .orElse(null);
        }
    }

}
