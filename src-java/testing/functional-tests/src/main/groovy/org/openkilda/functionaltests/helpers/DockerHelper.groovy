package org.openkilda.functionaltests.helpers

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import com.spotify.docker.client.messages.Container
import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.helpers.model.ContainerName

@Slf4j
class DockerHelper {

    DockerClient dockerClient
    String networkName

    DockerHelper(String host) {
        log.debug("Configuring docker client");
        if (host == 'localhost') {
            dockerClient = DefaultDockerClient.fromEnv().build()
        } else {
            dockerClient = DefaultDockerClient.fromEnv().uri(host).build()
        }
        log.debug("Connected to Docker Host: $host")

        networkName = getNetworkName()
        log.debug("Network name: $networkName")
    }

    String getContainerIp(ContainerName containerName) {
        "get container by name"(containerName).networkSettings().networks()[networkName].ipAddress()
    }

    void restartContainer(String containerId) {
        dockerClient.restartContainer(containerId)
    }

    void waitContainer(String containerId) {
        dockerClient.waitContainer(containerId)
    }

    String getContainerId(ContainerName containerName) {
        return "get container by name"(containerName).id()
    }

    void pauseContainer(String containerId) {
        dockerClient.pauseContainer(containerId)
    }

    void resumeContainer(String containerId) {
        dockerClient.unpauseContainer(containerId)
    }

    Container "get container by name" (ContainerName containerName) {
        return dockerClient.listContainers(ListContainersParam.allContainers()).find {
            it.names().contains(containerName.toString())
        }
    }

    private String getNetworkName() {
        dockerClient.listNetworks()*.name().find { it.contains('_default') && it.contains('kilda') }
    }
}
