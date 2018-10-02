/* Copyright 2018 Telstra Open Source
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

import org.openkilda.model.FlowStatus;
import org.openkilda.model.Node;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class RerouteService {

    private static final Logger logger = LoggerFactory.getLogger(RerouteService.class);

    private TransactionManager transactionManager;
    private FlowRepository flowRepository;

    public RerouteService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        this.flowRepository = repositoryFactory.createFlowRepository();
    }

    /**
     * Get set of active flow ids with affected path and set up status of this flows in FlowStatus.DOWN.
     *
     * @param node node.
     */
    public Set<String> getAffectedFlowIds(Node node) {
        logger.info("Get affected flow ids by node {}_{}", node.getSwitchId(), node.getPortNo());
        Set<String> affectedFlows = new HashSet<>();
        transactionManager.begin();
        try {
            flowRepository.findActiveFlowsByNode(node)
                    .forEach(flow -> {
                        flow.setStatus(FlowStatus.DOWN);
                        flowRepository.createOrUpdate(flow);
                        affectedFlows.add(flow.getFlowId());
                    });
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return new HashSet<>();
        }
        return affectedFlows;
    }

    /**
     * Get set of inactive flow ids.
     */
    public Set<String> getInactiveFlows() {
        logger.info("Get inactive flow ids");
        Set<String> inactiveFlows = new HashSet<>();
        flowRepository.findInactiveFlows()
                .forEach(flow -> inactiveFlows.add(flow.getFlowId()));
        return inactiveFlows;
    }
}
