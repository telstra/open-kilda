package org.openkilda.functionaltests.helpers

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import com.spotify.docker.client.messages.Container
import com.spotify.docker.client.messages.ContainerConfig
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class WfmManipulator {
    private static final String WFM_CONTAINER_NAME = "/wfm"
    private static final String KILDA_NETWORK_NAME = "open-kilda_default"
    /* Storm topologies require some time to fully roll after booting.
     * TODO(rtretiak): find a more reliable way to wait for the H-hour
     * Not respecting this wait may lead to subsequent tests instability
     */
    private static final int WFM_WARMUP_SECONDS = 180

    DockerClient dockerClient
    Container wfmContainer

    WfmManipulator() {
        dockerClient = DefaultDockerClient.fromEnv().build()
        wfmContainer = dockerClient.listContainers(ListContainersParam.allContainers()).find {
            it.names().contains(WFM_CONTAINER_NAME)
        }
    }

    /**
     * Redeploys all the storm topologies. Takes about 10 minutes
     * @param wait whether to wait for redeploy to finish
     */
    def restartWfm(boolean wait = true) {
        log.warn "Restarting wfm"
        dockerClient.restartContainer(wfmContainer.id())
        if (wait) {
            dockerClient.waitContainer(wfmContainer.id())
            TimeUnit.SECONDS.sleep(WFM_WARMUP_SECONDS)
        }
    }

    def killTopology(String topologyName) {
        log.warn "Killing wfm $topologyName topology"
        manipulateTopology("kill", topologyName)
    }

    def deployTopology(String topologyName) {
        log.warn "Deploying wfm $topologyName topology"
        manipulateTopology("deploy", topologyName)
    }

    private def manipulateTopology(String action, String topologyName) {
        assert action in ["kill", "deploy"]
        def container
        def image
        try {
            image = dockerClient.commitContainer(wfmContainer.id(), "kilda/testing", null,
                    ContainerConfig.builder()
                                   .cmd(['/bin/bash', '-c',
                                         "PATH=\${PATH}:/opt/storm/bin;make -C /app $action-$topologyName".toString()])
                                   .build(), null, null)
            container = dockerClient.createContainer(ContainerConfig.builder().image(image.id()).build())
            def network = dockerClient.listNetworks().find { it.name() == KILDA_NETWORK_NAME }
            dockerClient.connectToNetwork(container.id(), network.id())
            dockerClient.startContainer(container.id())
        } finally {
            container && dockerClient.waitContainer(container.id())
            container && dockerClient.removeContainer(container.id())
            image && dockerClient.removeImage(image.id())
        }
    }
}
