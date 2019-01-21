package org.openkilda.functionaltests.spec.resilience

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""
Storm Lifecycle Management: verifies system behavior during restart of certain parts of Storm. This is required to 
simulate prod deployments, which are done on the live environment.
""")
@Ignore("On demand test. Takes too much time")
class StormLcmSpec extends BaseSpecification {
    private static final String WFM_CONTAINER_NAME = "/wfm"

    @Shared
    DockerClient dockerClient

    def setupOnce() {
        //since we simulate storm restart by restarting the docker container, for now this is only possible on virtual
        //TODO(rtretiak): this can possibly be achieved for 'hardware' via lock-keeper instance
        requireProfiles("virtual")
        dockerClient = DefaultDockerClient.fromEnv().build()
    }

    /**
     * This test takes quite some time (~7+ minutes) since it redeploys all the storm topologies.
     * Aborting it in the middle of execution will lead to Kilda malfunction.
     * Another problem is that system is not stable for about 5 minutes after storm topologies are up, so
     * this may break execution of subsequent tests
     */
    def "System survives Storm topologies restart"() {
        given: "Existing flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        when: "Storm topologies are restarted"
        def wfmContainer = dockerClient.listContainers(ListContainersParam.allContainers()).find {
            it.names().contains(WFM_CONTAINER_NAME)
        }
        assert wfmContainer
        dockerClient.restartContainer(wfmContainer.id())
        dockerClient.waitContainer(wfmContainer.id())
        //wait until it starts to respond
        Wrappers.wait(15) {
            northbound.activeSwitches
            true
        }

        then: "Network topology is unchanged"
        northbound.activeSwitches.size() == topology.activeSwitches.size()
        northbound.getAllLinks().findAll {
            it.state == IslChangeType.DISCOVERED
        }.size() == topology.islsForActiveSwitches.size() * 2

        and: "Flow remains valid"
        northbound.validateFlow(flow.id).each { direction ->
            assert direction.discrepancies.findAll { it.field != "meterId" }.empty
        }

        and: "Flow can be updated"
        flowHelper.updateFlow(flow.id, flow.tap { it.source.vlanId++ })
        northbound.validateFlow(flow.id).each { direction ->
            assert direction.discrepancies.findAll { it.field != "meterId" }.empty
        }

        and: "cleanup: remove flow"
        flowHelper.deleteFlow(flow.id)
    }
}
