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

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import cucumber.api.java.en.When;
import org.apache.log4j.Logger;

public class FloodlightLcm {
    private static final Logger LOGGER = Logger.getLogger(StormTopologyLCM.class);

    private static final String FL_CONTAINER_NAME = "/floodlight";

    private Container flContainer;
    private DockerClient dockerClient;

    public FloodlightLcm() throws Exception {
        dockerClient = DefaultDockerClient.fromEnv().build();
        flContainer = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
                .stream()
                .filter(container ->
                        container.names().contains(FL_CONTAINER_NAME))
                .findFirst().orElseThrow(
                        () -> new IllegalStateException("Can't find floodlight container"));
    }

    @When("restart floodlight container")
    public void restartFl() throws DockerException, InterruptedException {
        dockerClient.restartContainer(flContainer.id());
    }

    @When("stop floodlight container")
    public void stopFl() throws DockerException, InterruptedException {
        dockerClient.stopContainer(flContainer.id(), 5);
    }

    @When("start floodlight container")
    public void startFl() throws DockerException, InterruptedException {
        dockerClient.startContainer(flContainer.id());
    }
}
