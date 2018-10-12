package org.openkilda.atdd.floodlight;

import static com.google.common.base.Charsets.UTF_8;
import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.utils.controller.ControllerUtils;
import org.openkilda.atdd.utils.controller.CoreFlowEntry;
import org.openkilda.atdd.utils.controller.DpIdEntriesList;
import org.openkilda.atdd.utils.controller.DpIdNotFoundException;
import org.openkilda.atdd.utils.controller.FloodlightQueryException;
import org.openkilda.atdd.utils.controller.StaticFlowEntry;
import org.openkilda.topo.TestUtils;
import org.openkilda.topo.TopologyHelp;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;

import java.util.List;
import javax.ws.rs.ProcessingException;

public class ClearTablesTest {
    private static final String SWITCH_DPID = "00:01:00:00:00:00:00:01";
    private static final String ETHERNET_DEST_ADDRESS = "01:23:45:67:89:0a";

    private final ControllerUtils controllerUtils;

    public ClearTablesTest() throws  Exception {
        controllerUtils = new ControllerUtils();
    }

    @Given("^started floodlight container")
    public void givenStartedContainer() throws Exception {
        TestUtils.clearEverything();
    }

    @Given("^created simple topology from two switches")
    public void createTopology() throws Exception {
        String topology =
                IOUtils.toString(this.getClass().getResourceAsStream("/topologies/simple-topology.json"), UTF_8);
        assertTrue(TopologyHelp.createMininetTopology(topology));
    }

    @Given("^added custom flow rules")
    public void addRules() throws Exception {
        StaticFlowEntry flow = new StaticFlowEntry("reboot-survive-flow", SWITCH_DPID)
                .withCookie(0xf0000001L)
                .withInPort("1")
                .withEthDest(ETHERNET_DEST_ADDRESS)
                .withAction("drop");
        controllerUtils.addStaticFlow(flow);
    }

    @When("^floodlight controller is restarted")
    public void restartFloodlight() throws Exception {
        controllerUtils.restart();
    }

    @Then("^flow rules should not be cleared up")
    public void checkFlowRules() throws FloodlightQueryException {
        DpIdEntriesList staticEntries = controllerUtils.listStaticEntries();

        // All rules(static) must be dropped during controller restart
        Assert.assertEquals(staticEntries.size(), 0);

        // It is possible that controllerUtils.listCoreFlows call is done before switch is connected to FloodLight. In
        // this case we will catch DpIdNotFoundException. So we had to make several attempts to get the list of flows
        // from target switch.
        int i = 0;
        List<CoreFlowEntry> flows = null;
        while (i++ < 5) {
            try {
                flows = controllerUtils.listCoreFlows(SWITCH_DPID);
                break;
            } catch (DpIdNotFoundException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) { }
            }
        }
        if (flows == null) {
            throw new ProcessingException("Can't reads flows list from FloodLight");
        }

        boolean isFlowSurvived = false;
        for (CoreFlowEntry entry : flows) {
            if (!entry.match.ethDest.equals(ETHERNET_DEST_ADDRESS)) {
                continue;
            }

            isFlowSurvived = true;
            break;
        }

        assertTrue("The test flow didn't survive controller reboot", isFlowSurvived);
    }
}
