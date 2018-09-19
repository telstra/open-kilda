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

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.flow.FlowUtils.dumpFlows;
import static org.openkilda.flow.FlowUtils.getLinkBandwidth;
import static org.openkilda.flow.FlowUtils.restoreFlows;
import static org.openkilda.northbound.dto.links.LinkStatus.FAILED;

import org.openkilda.LinksUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.provider.UnroutablePathException;
import org.openkilda.topo.TopologyHelp;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class FlowPathTest {
    private static final String fileName = "topologies/multi-path-topology.json";
    private static final List<FlowPair<String, String>> shortestPathLinks = Arrays.asList(
            new FlowPair<>("00:00:00:00:00:00:00:07", "1"), new FlowPair<>("00:00:00:00:00:00:00:03", "2"),
            new FlowPair<>("00:00:00:00:00:00:00:02", "2"), new FlowPair<>("00:00:00:00:00:00:00:03", "1"));
    private static final List<FlowPair<String, String>> alternativePathLinks = Arrays.asList(
            new FlowPair<>("00:00:00:00:00:00:00:02", "3"), new FlowPair<>("00:00:00:00:00:00:00:04", "1"),
            new FlowPair<>("00:00:00:00:00:00:00:05", "1"), new FlowPair<>("00:00:00:00:00:00:00:04", "2"),
            new FlowPair<>("00:00:00:00:00:00:00:05", "2"), new FlowPair<>("00:00:00:00:00:00:00:06", "1"),
            new FlowPair<>("00:00:00:00:00:00:00:06", "2"), new FlowPair<>("00:00:00:00:00:00:00:07", "3"));
    static final FlowPair<PathInfoData, PathInfoData> expectedShortestPath = new FlowPair<>(
            new PathInfoData(0L, Arrays.asList(
                    new PathNode(new SwitchId(2L), 2, 0, 0L),
                    new PathNode(new SwitchId(3L), 1, 1, 0L),
                    new PathNode(new SwitchId(3L), 2, 2, 0L),
                    new PathNode(new SwitchId(7L), 1, 3, 0L))),
            new PathInfoData(0L, Arrays.asList(
                    new PathNode(new SwitchId(7L), 1, 0, 0L),
                    new PathNode(new SwitchId(3L), 2, 1, 0L),
                    new PathNode(new SwitchId(3L), 1, 2, 0L),
                    new PathNode(new SwitchId(2L), 2, 3, 0L))));
    static final FlowPair<PathInfoData, PathInfoData> expectedAlternatePath = new FlowPair<>(
            new PathInfoData(0L, Arrays.asList(
                    new PathNode(new SwitchId(2L), 3, 0, 0L),
                    new PathNode(new SwitchId(4L), 1, 1, 0L),
                    new PathNode(new SwitchId(4L), 2, 2, 0L),
                    new PathNode(new SwitchId(5L), 1, 3, 0L),
                    new PathNode(new SwitchId(5L), 2, 0, 0L),
                    new PathNode(new SwitchId(6L), 1, 1, 0L),
                    new PathNode(new SwitchId(6L), 2, 2, 0L),
                    new PathNode(new SwitchId(7L), 3, 3, 0L))),
            new PathInfoData(0L, Arrays.asList(
                    new PathNode(new SwitchId(7L), 3, 3, 0L),
                    new PathNode(new SwitchId(6L), 2, 2, 0L),
                    new PathNode(new SwitchId(6L), 1, 1, 0L),
                    new PathNode(new SwitchId(5L), 2, 0, 0L),
                    new PathNode(new SwitchId(5L), 1, 3, 0L),
                    new PathNode(new SwitchId(4L), 2, 2, 0L),
                    new PathNode(new SwitchId(4L), 1, 1, 0L),
                    new PathNode(new SwitchId(2L), 3, 0, 0L))));

    private String previousLastUpdated;
    private String actualFlowName;
    private long preStart;
    private long start;

    @Given("^a multi-path topology$")
    public void multiPathTopology() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        String json;

        try {
            json = new String(Files.readAllBytes(file.toPath()));
        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to read the topology file '%s'.", fileName), ex);
        }

        preStart = System.currentTimeMillis();
        assertTrue(TopologyHelp.CreateMininetTopology(json));
        start = System.currentTimeMillis();
    }

    @When("^all links have available bandwidth (\\d+)$")
    public void checkAvailableBandwidth(long expectedAvailableBandwidth) throws InterruptedException {
        List<LinkDto> links = LinksUtils.dumpLinks();
        for (LinkDto link : links) {
            long actualBandwidth = getBandwidth(expectedAvailableBandwidth,
                    new SwitchId(link.getPath().get(0).getSwitchId()),
                    String.valueOf(link.getPath().get(0).getPortNo()));
            assertEquals(expectedAvailableBandwidth, actualBandwidth);
        }
    }

    @Then("^shortest path links available bandwidth have available bandwidth (\\d+)$")
    public void checkShortestPathAvailableBandwidthDecreased(long expectedAvailableBandwidth)
            throws InterruptedException {
        for (FlowPair<String, String> expectedLink : shortestPathLinks) {
            long actualBandwidth = getBandwidth(expectedAvailableBandwidth,
                    new SwitchId(expectedLink.getLeft()), expectedLink.getRight());
            assertEquals(expectedAvailableBandwidth, actualBandwidth);
        }
    }

    @Then("^alternative path links available bandwidth have available bandwidth (\\d+)$")
    public void checkAlternativePathAvailableBandwidthDecreased(long expectedAvailableBandwidth)
            throws InterruptedException {
        for (FlowPair<String, String> expectedLink : alternativePathLinks) {
            long actualBandwidth = getBandwidth(expectedAvailableBandwidth,
                    new SwitchId(expectedLink.getLeft()), expectedLink.getRight());
            assertEquals(expectedAvailableBandwidth, actualBandwidth);
        }
    }

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) path correct$")
    public void flowPathCorrect(String flowId, String sourceSwitch, int sourcePort, int sourceVlan,
                                String destinationSwitch, int destinationPort, int destinationVlan, long bandwidth)
            throws UnroutablePathException, InterruptedException, RecoverableException {
        Flow flow =
                new Flow(FlowUtils.getFlowName(flowId), bandwidth, false, flowId,
                        new SwitchId(sourceSwitch), sourcePort, sourceVlan, new SwitchId(destinationSwitch),
                        destinationPort, destinationVlan);
        FlowPair<PathInfoData, PathInfoData> path = FlowUtils.getFlowPath(flow);
        System.out.println(path);
        assertEquals(expectedShortestPath, path);
    }

    private long getBandwidth(long expectedBandwidth, SwitchId srcSwitch, String srcPort) throws InterruptedException {
        long actualBandwidth = getLinkBandwidth(srcSwitch, srcPort);
        if (actualBandwidth != expectedBandwidth) {
            TimeUnit.SECONDS.sleep(2);
            actualBandwidth = getLinkBandwidth(srcSwitch, srcPort);
        }
        return actualBandwidth;
    }

    @Given("^topology contains (\\d+) links$")
    public void topologyContainsLinks(int expectedLinks) throws InterruptedException {
        // give WFM time to send discovery requests and notify TE.
        TimeUnit.SECONDS.sleep(4);
        waitForVerifiedLinks(expectedLinks);
    }

    private void waitForVerifiedLinks(int expectedLinks) throws InterruptedException {
        long actualLinks = 0;

        for (int i = 0; i < 10; i++) {
            List<LinkDto> links = LinksUtils.dumpLinks();

            // Count verified and healthy links
            actualLinks = links.stream()
                    .filter(link -> link.getState() != FAILED)
                    .filter(link -> link.getPath().stream()
                            .noneMatch(pathNode -> pathNode.getSeqId() == 0
                                    && pathNode.getSegLatency() == null))
                    .count();

            if (actualLinks == expectedLinks) {
                return;
            }

            TimeUnit.SECONDS.sleep(3);
        }

        assertEquals(expectedLinks, actualLinks);
    }

    @When("^delete mininet topology$")
    public void deleteMininetTopology() {
        TopologyHelp.DeleteMininetTopology();
    }

    @When("^(\\d+) seconds passed$")
    public void secondsPassed(int timeout) throws InterruptedException {
        System.out.println(format("\n==> Sleep for %d seconds", timeout));
        System.out.println(format("===> Sleep start at = %d", System.currentTimeMillis()));
        TimeUnit.SECONDS.sleep(timeout);
        System.out.println(format("===> Sleep end at = %d", System.currentTimeMillis()));
    }

    @Then("^flow (.*) has updated timestamp$")
    public void flowRestoredHasValidLastUpdatedTimestamp(String flowId) throws Throwable {
        List<Flow> flows = dumpFlows();

        if (flows == null || flows.isEmpty()) {
            TimeUnit.SECONDS.sleep(2);
            flows = dumpFlows();
        }

        assertNotNull(flows);
        assertEquals(2, flows.size());

        Flow flow = flows.get(0);
        String currentLastUpdated = flow.getLastUpdated();
        System.out.println(format("=====> Flow %s previous timestamp = %s", flowId, previousLastUpdated));
        System.out.println(format("=====> Flow %s current timestamp = %s", flowId, currentLastUpdated));

        assertNotEquals(previousLastUpdated, currentLastUpdated);
        previousLastUpdated = currentLastUpdated;
    }

    @When("^restore flows$")
    public void flowRestore() {
        restoreFlows();
    }
}
