package org.openkilda.functionaltests.helpers

import java.util.regex.Pattern

import static org.openkilda.functionaltests.helpers.model.ContainerName.STORM
import static org.openkilda.functionaltests.helpers.model.ContainerName.WFM

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class WfmManipulator {
    /* Storm topologies require some time to fully roll after booting.
     * TODO(rtretiak): find a more reliable way to wait for the H-hour
     * Not respecting this wait may lead to subsequent tests instability
     */
    private static final int WFM_WARMUP_SECONDS = 180

    DockerClient dockerClient
    DockerHelper dockerHelper
    String wfmContainerId

    WfmManipulator(String dockerHost) {
        dockerHelper = new DockerHelper(dockerHost)
        dockerClient = dockerHelper.dockerClient
        wfmContainerId = dockerHelper."get container by name"(WFM).id()
    }

    /**
     * Redeploys all the storm topologies. Takes about 10 minutes
     * @param wait whether to wait for redeploy to finish
     */
    def restartWfm(boolean wait = true) {
        log.warn "Restarting wfm"
        dockerHelper.restartContainer(wfmContainerId)
        if (wait) {
            dockerHelper.waitContainer(wfmContainerId)
            TimeUnit.SECONDS.sleep(WFM_WARMUP_SECONDS)
        }
    }

    String getStormActualNetworkTopology() {
        String stormUIContainerId = dockerHelper."get container by name"(STORM).id()
        String[] topologiesList = ["sh", "-c", "PATH=\${PATH}:/opt/storm/bin; storm list | grep network"]
        String commandOutput = dockerHelper.execute(stormUIContainerId, topologiesList)
        Pattern pattern = ~/network\w*/
        assert pattern.matcher(commandOutput).find(), "Something went wrong, network topology name has not been retrieved: \n $commandOutput"
        //in the blue/green mode, all topologies have a name format: topologyName_mode (mode: blue/green, ex.: network_blue)
        // to deploy/kill topology use the format topologyName-mode (ex. network-blue)
        return pattern.matcher(commandOutput).findAll().first().toString().replace("_", "-")
    }

    def killTopology(String topologyName) {
        log.warn "Killing wfm $topologyName topology"
        manipulateTopology("kill", topologyName)
        log.info "WFM $topologyName topology has been deleted"
    }

    def deployTopology(String topologyName) {
        log.warn "Deploying wfm $topologyName topology"
        manipulateTopology("deploy", topologyName)
        log.info("WFM $topologyName topology has been deployed")
    }

    private def manipulateTopology(String action, String topologyName) {
        assert action in ["kill", "deploy"]
        def container
        def image
        try {
            image = dockerClient.commitContainer(wfmContainerId, "kilda/testing", null,
                    ContainerConfig.builder()
                                   .cmd(['/bin/bash', '-c',
                                         "PATH=\${PATH}:/opt/storm/bin;make -C /app $action-$topologyName".toString()])
                                   .build(), null, null)
            container = dockerClient.createContainer(ContainerConfig.builder().image(image.id()).build())
            def network = dockerClient.listNetworks().find { it.name() == dockerHelper.networkName }
            dockerClient.connectToNetwork(container.id(), network.id())
            dockerClient.startContainer(container.id())
        } finally {
            container && dockerClient.waitContainer(container.id())
            container && dockerClient.removeContainer(container.id())
            image && dockerClient.removeImage(image.id())
        }
    }
}
