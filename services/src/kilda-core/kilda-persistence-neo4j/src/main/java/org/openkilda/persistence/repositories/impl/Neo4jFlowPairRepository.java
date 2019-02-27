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
import java.util.Set;
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
    public boolean exists(String flowId) {
        return flowRepository.exists(flowId);
    }

    @Override
    public Optional<FlowPair> findById(String flowId) {
        return flowRepository.findById(flowId).map(this::toFlowPair);
    }

    @Override
    public Collection<FlowPair> findByGroupId(String flowGroupId) {
        return flowRepository.findByGroupId(flowGroupId).stream()
                .map(this::toFlowPair)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowPair> findWithPeriodicPingsEnabled() {
        return flowRepository.findWithPeriodicPingsEnabled().stream()
                .map(this::toFlowPair)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowPair> findByEndpoint(SwitchId switchId, int port) {
        return flowRepository.findByEndpoint(switchId, port).stream()
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

    @Override
    public Set<String> findFlowIdsBySwitch(SwitchId switchId) {
        return flowRepository.findFlowIdsBySwitch(switchId);
    }

    @Override
    public Collection<FlowPair> findAll() {
        return flowRepository.findAll().stream()
                .map(this::toFlowPair)
                .collect(Collectors.toList());
    }

    @Override
    public void createOrUpdate(FlowPair entity) {
        flowRepository.createOrUpdate(entity.getFlowEntity());
        if (entity.getForwardTransitVlanEntity() != null) {
            transitVlanRepository.createOrUpdate(entity.getForwardTransitVlanEntity());
        }
        if (entity.getReverseTransitVlanEntity() != null) {
            transitVlanRepository.createOrUpdate(entity.getReverseTransitVlanEntity());
        }
    }

    @Override
    public void delete(FlowPair entity) {
        flowRepository.delete(entity.getFlowEntity());
        if (entity.getForwardTransitVlanEntity() != null) {
            transitVlanRepository.delete(entity.getForwardTransitVlanEntity());
        }
        if (entity.getReverseTransitVlanEntity() != null) {
            transitVlanRepository.delete(entity.getReverseTransitVlanEntity());
        }
    }

    private FlowPair toFlowPair(Flow flow) {
        TransitVlan forwardTransitVlan = transitVlanRepository.findByPathId(flow.getForwardPathId()).orElse(null);
        TransitVlan reverseTransitVlan = transitVlanRepository.findByPathId(flow.getReversePathId()).orElse(null);

        return new FlowPair(flow, forwardTransitVlan, reverseTransitVlan);
    }
}
