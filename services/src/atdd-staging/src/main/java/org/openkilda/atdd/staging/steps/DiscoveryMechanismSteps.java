package org.openkilda.atdd.staging.steps;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import cucumber.api.java.en.Then;
import cucumber.api.java8.En;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openkilda.atdd.staging.service.FloodlightService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

public class DiscoveryMechanismSteps implements En {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryMechanismSteps.class);

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private TopologyEngineService topologyEngineService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Then("^all provided switches should be discovered")
    public void checkDiscoveredSwitches() {
        List<SwitchInfoData> dumpedSwitches = topologyEngineService.getAllSwitches();
        List<SwitchInfoData> discoveredSwitches = dumpedSwitches.stream()
                .filter(sw -> sw.getState().isActive())
                .collect(Collectors.toList());
        assertFalse("No switches were discovered", CollectionUtils.isEmpty(discoveredSwitches));

        List<TopologyDefinition.Switch> expectedSwitches = topologyDefinition.getSwitches();
        assertFalse("Expected switches should be provided", expectedSwitches.isEmpty());
        assertEquals("Expected and discovered switches amount are not the same", expectedSwitches.size(),
                discoveredSwitches.size());

        expectedSwitches.forEach(switchDef -> {
            SwitchInfoData switchInfoData = discoveredSwitches.stream()
                    .filter(sw -> StringUtils.equalsIgnoreCase(sw.getSwitchId(), switchDef.getDpId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(String.format("Switch %s is not discovered", switchDef.getDpId()), switchInfoData);
            assertTrue(String.format("Switch %s should be active", switchDef.getDpId()),
                    switchInfoData.getState().isActive());
        });
    }

    @Then("^all provided links should be detected")
    public void checkDiscoveredLinks() {
        List<IslInfoData> discoveredLinks = topologyEngineService.getAllLinks();
        List<TopologyDefinition.Isl> expectedLinks = topologyDefinition.getIsls();

        if (CollectionUtils.isEmpty(discoveredLinks) && expectedLinks.isEmpty()) {
            LOGGER.info("There are no links discovered as expected");
            return;
        }

        assertFalse("Links were not discovered / not provided",
                CollectionUtils.isEmpty(discoveredLinks) || expectedLinks.isEmpty());
        List<TopologyDefinition.Isl> result = expectedLinks.stream()
                .filter(link -> !linkIsPresent(link, discoveredLinks))
                .collect(Collectors.toList());

        //print out links that were not discovered
        if (!result.isEmpty()) {
            result.forEach(link ->
                    LOGGER.error("Not found ISL between {} - {}",
                            link.getSrcSwitch(), link.getDstSwitch()));
        }
        assertTrue(String.format("%s link were not discovered", result.size()), result.isEmpty());

        assertThat(String.format("There were %s more links discovered than expected",
                discoveredLinks.size() - expectedLinks.size()), expectedLinks.size(), is(discoveredLinks.size()));
    }

    @Then("^floodlight should not find redundant switches")
    public void checkFloodlightSwitches() {
        //todo: implement checking switches
    }

    @Then("floodlight should not find unexpected links")
    public void checkFloodlightLinks() {
        //todo: implement checking isls

    }

    private boolean linkIsPresent(TopologyDefinition.Isl expectedLink, List<IslInfoData> discoveredLinks) {
        String srcSwitchId = expectedLink.getSrcSwitch().getDpId();
        String dstSwitchId = expectedLink.getDstSwitch().getDpId();
        return discoveredLinks.stream()
                .anyMatch(isl -> {
                    //todo: need to check port as well
                    return srcSwitchId.equalsIgnoreCase(isl.getPath().get(0).getSwitchId()) &&
                            dstSwitchId.equalsIgnoreCase(isl.getPath().get(1).getSwitchId());
                });
    }
}
