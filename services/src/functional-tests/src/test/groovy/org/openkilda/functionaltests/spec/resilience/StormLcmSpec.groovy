package org.openkilda.functionaltests.spec.resilience

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.payload.flow.FlowPayload

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import spock.lang.Narrative
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Narrative("""
Storm Lifecycle Management: verifies system behavior after restart of WFM. This is required to simulate prod 
deployments, which are done on the live environment. Before restart the system will have some data (created flows etc.),
verify their consistency after restart.
""")
/**
 * This test takes quite some time (~10+ minutes) since it redeploys all the storm topologies.
 * Aborting it in the middle of execution may lead to Kilda malfunction.
 */
class StormLcmSpec extends BaseSpecification {
    private static final String WFM_CONTAINER_NAME = "/wfm"

    /* Storm topologies require some time to fully roll after booting.
     * TODO(rtretiak): find a more reliable way to wait for the H-hour
     * Not respecting this wait may lead to subsequent tests instability
     */
    private static final int WFM_WARMUP_SECONDS = 120

    @Shared
    DockerClient dockerClient

    def setupOnce() {
        //since we simulate storm restart by restarting the docker container, for now this is only possible on virtual
        //TODO(rtretiak): this can possibly be achieved for 'hardware' via lock-keeper instance
        requireProfiles("virtual")
        dockerClient = DefaultDockerClient.fromEnv().build()
    }

    def "System survives Storm topologies restart"() {
        given: "Non-empty system with some flows created"
        List<FlowPayload> flows = []
        def flowsAmount = topology.activeSwitches.size() * 3
        flowsAmount.times {
            def flow = flowHelper.randomFlow(*topologyHelper.getRandomSwitchPair(false), false, flows)
            flow.maximumBandwidth = 500000
            flowHelper.addFlow(flow)
            flows << flow
        }

        and: "Database dump"
        def nodesDump = database.dumpAllNodes()
        def relationsDump = database.dumpAllRelations()

        when: "Storm topologies are restarted"
        def wfmContainer = dockerClient.listContainers(ListContainersParam.allContainers()).find {
            it.names().contains(WFM_CONTAINER_NAME)
        }
        assert wfmContainer
        dockerClient.restartContainer(wfmContainer.id())
        dockerClient.waitContainer(wfmContainer.id())
        TimeUnit.SECONDS.sleep(WFM_WARMUP_SECONDS)

        then: "Database nodes and relations are unchanged"
        def newNodes = database.dumpAllNodes()
        def newRelation = database.dumpAllRelations()
        expect newNodes, sameBeanAs(nodesDump)
        expect newRelation, sameBeanAs(relationsDump).ignoring("time_modify").ignoring("latency")

        and: "Flows remain valid in terms of installed rules and meters"
        flows.each { flow ->
            northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }
        }

        and: "Flow can be updated"
        def flowToUpdate = flows[0]
        //expect enough free vlans here, ignore used switch-ports for simplicity of search
        def unusedVlan = (flowHelper.allowedVlans - flows.collectMany { [it.source.vlanId, it.destination.vlanId] })[0]
        flowHelper.updateFlow(flowToUpdate.id, flowToUpdate.tap { it.source.vlanId = unusedVlan })
        northbound.validateFlow(flowToUpdate.id).each { direction -> assert direction.asExpected }

        and: "Cleanup: remove flows"
        flows.each { flowHelper.deleteFlow(it.id) }
    }
}
