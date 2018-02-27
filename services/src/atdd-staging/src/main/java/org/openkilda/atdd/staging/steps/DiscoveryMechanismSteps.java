package org.openkilda.atdd.staging.steps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cucumber.api.java.en.Then;
import cucumber.api.java8.En;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.openkilda.atdd.staging.service.FloodlightService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.topo.ITopology;
import org.openkilda.topo.Switch;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscoveryMechanismSteps implements En {

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private TopologyEngineService topologyEngineService;

    @Autowired
    private ITopology topology;

    @Then("^all provided switches should be discovered")
    public void checkDiscoveredSwitches() {
        List<SwitchInfoData> discoveredSwitches = topologyEngineService.dumpSwitches();
        assertFalse("No switches were discovered", CollectionUtils.isEmpty(discoveredSwitches));

        Map<String, Switch> expectedSwitches = new HashMap<>(topology.getSwitches());
        assertEquals("Expected and discovered switches amount are not the same", expectedSwitches.size(),
                discoveredSwitches.size());

        expectedSwitches.keySet().forEach(switchId -> {
            SwitchInfoData switchInfoData = discoveredSwitches.stream()
                    .filter(sw -> StringUtils.equals(sw.getSwitchId(), switchId))
                    .findFirst()
                    .get();
            assertNotNull(String.format("Switch %s is not discovered", switchId), switchInfoData);
            assertTrue(String.format("Switch %s should be active", switchId), switchInfoData.getState().isActive());
        });
    }

    @Then("^all provided links should be detected")
    public void checkDiscoveredLinks() {
        List<IslInfoData> discoveredLinks = topologyEngineService.dumpLinks();
        assertFalse(discoveredLinks.isEmpty());
    }

    @Then("^floodlight should not find redundant switches")
    public void checkFloodlightSwitches() {
        //todo: implement checking switches
    }

    @Then("floodlight should not find unexpected links")
    public void checkFloodlightLinks() {
        //todo: implement checking isls

    }
}
