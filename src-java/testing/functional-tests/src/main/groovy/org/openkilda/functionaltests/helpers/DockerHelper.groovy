package org.openkilda.functionaltests.helpers

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import groovy.util.logging.Slf4j

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

    String getContainerIp(String containerName) {
        dockerClient.listContainers(ListContainersParam.allContainers()).find {
            it.names().contains("/" + containerName)
        }.networkSettings().networks()[networkName].ipAddress()
    }

    void restartContainer(String containerId) {
        dockerClient.restartContainer(containerId)
    }

    void waitContainer(String containerId) {
        dockerClient.waitContainer(containerId)
    }

    private String getNetworkName() {
        dockerClient.listNetworks()*.name().find { it.contains('_default')}
    }
}
