/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.reroute.service;

import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public class RerouteService {

    private FlowRepository flowRepository;

    public RerouteService(RepositoryFactory repositoryFactory) {
        this.flowRepository = repositoryFactory.createFlowRepository();
    }

    /**
     * Get set of active flow ids with affected path.
     *
     * @param switchId switch id.
     * @param port     port.
     * @return set of active flow ids with affected path.
     */
    public Set<Flow> getAffectedFlowIds(SwitchId switchId, int port) {
        log.info("Get affected flows by node {}_{}", switchId, port);
        return flowRepository.findActiveFlowsWithPortInPath(switchId, port);
    }

    /**
     * Get set of inactive flow ids.
     */
    public Set<Flow> getInactiveFlows() {
        log.info("Get inactive flow ids");
        return flowRepository.findDownFlows();
    }
}
