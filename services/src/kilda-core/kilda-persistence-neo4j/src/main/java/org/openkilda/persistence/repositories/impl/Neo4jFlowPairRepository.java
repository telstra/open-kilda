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

package org.openkilda.persistence.repositories.impl;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Neo4J OGM implementation of {@link FlowPairRepository}.
 *
 * @deprecated Must be replaced with new model entities: {@link org.openkilda.model.Flow}
 */
@Deprecated
public class Neo4jFlowPairRepository implements FlowPairRepository {

    private FlowRepository flowRepository;
    private TransitVlanRepository transitVlanRepository;

    public Neo4jFlowPairRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        flowRepository = new Neo4jFlowRepository(sessionFactory, transactionManager);
        transitVlanRepository = new Neo4jTransitVlanRepository(sessionFactory, transactionManager);
    }

    @Override
    public Optional<FlowPair> findById(String flowId) {
        return flowRepository.findById(flowId).map(this::toFlowPair);
    }

    @Override
    public Collection<FlowPair> findWithPeriodicPingsEnabled() {
        return flowRepository.findWithPeriodicPingsEnabled().stream()
                .map(this::toFlowPair)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowPair> findWithSegmentInPath(SwitchId srcSwitchId, int srcPort,
                                                      SwitchId dstSwitchId, int dstPort) {
        return flowRepository.findWithPathSegment(srcSwitchId, srcPort, dstSwitchId, dstPort).stream()
                .map(this::toFlowPair)
                .collect(Collectors.toList());
    }

    private FlowPair toFlowPair(Flow flow) {
        TransitVlan forwardTransitVlan = transitVlanRepository.findByPathId(flow.getForwardPathId()).stream()
                .findAny().orElse(null);
        TransitVlan reverseTransitVlan = transitVlanRepository.findByPathId(flow.getReversePathId()).stream()
                .findAny().orElse(null);

        return new FlowPair(flow, forwardTransitVlan, reverseTransitVlan);
    }
}
