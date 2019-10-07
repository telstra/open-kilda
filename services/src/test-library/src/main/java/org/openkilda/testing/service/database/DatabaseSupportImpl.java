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

package org.openkilda.testing.service.database;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.openkilda.testing.Constants.DEFAULT_COST;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.neo4j.ogm.model.Property;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.response.model.RelationshipModel;
import org.neo4j.ogm.session.Session;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DatabaseSupportImpl implements Database {
    private static final int DEFAULT_DEPTH = 7;

    private final TransactionManager transactionManager;
    private final IslRepository islRepository;
    private final SwitchRepository switchRepository;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final FlowPairRepository flowPairRepository;
    private final TransitVlanRepository transitVlanRepository;

    public DatabaseSupportImpl(PersistenceManager persistenceManager) {
        this.transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        flowPairRepository = repositoryFactory.createFlowPairRepository();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();
    }

    /**
     * Updates max_bandwidth property on a certain ISL.
     *
     * @param islToUpdate ISL to be changed
     * @param value max bandwidth to set
     * @return true if at least 1 ISL was affected.
     */
    @Override
    public boolean updateIslMaxBandwidth(Isl islToUpdate, long value) {
        return transactionManager.doInTransaction(() -> {
            Optional<org.openkilda.model.Isl> isl = islRepository.findByEndpoints(
                    islToUpdate.getSrcSwitch().getDpId(), islToUpdate.getSrcPort(),
                    islToUpdate.getDstSwitch().getDpId(), islToUpdate.getDstPort());
            isl.ifPresent(link -> {
                link.setMaxBandwidth(value);
                islRepository.createOrUpdate(link);
            });

            return isl.isPresent();
        });
    }

    /**
     * Updates available_bandwidth property on a certain ISL.
     *
     * @param islToUpdate ISL to be changed
     * @param value available bandwidth to set
     * @return true if at least 1 ISL was affected.
     */
    @Override
    public boolean updateIslAvailableBandwidth(Isl islToUpdate, long value) {
        return transactionManager.doInTransaction(() -> {
            Optional<org.openkilda.model.Isl> isl = islRepository.findByEndpoints(
                    islToUpdate.getSrcSwitch().getDpId(), islToUpdate.getSrcPort(),
                    islToUpdate.getDstSwitch().getDpId(), islToUpdate.getDstPort());
            isl.ifPresent(link -> {
                link.setAvailableBandwidth(value);
                islRepository.createOrUpdate(link);
            });

            return isl.isPresent();
        });
    }

    /**
     * Updates cost property on a certain ISL.
     *
     * @param islToUpdate ISL to be changed
     * @param value cost to set
     * @return true if at least 1 ISL was affected.
     */
    @Override
    public boolean updateIslCost(Isl islToUpdate, int value) {
        return transactionManager.doInTransaction(() -> {
            Optional<org.openkilda.model.Isl> isl = islRepository.findByEndpoints(
                    islToUpdate.getSrcSwitch().getDpId(), islToUpdate.getSrcPort(),
                    islToUpdate.getDstSwitch().getDpId(), islToUpdate.getDstPort());
            isl.ifPresent(link -> {
                link.setCost(value);
                islRepository.createOrUpdate(link);
            });

            return isl.isPresent();
        });
    }

    /**
     * Set ISL's max bandwidth to be equal to its speed (the default situation).
     *
     * @param islToUpdate ISL to be changed
     * @return true if at least 1 ISL was affected
     */
    @Override
    public boolean resetIslBandwidth(Isl islToUpdate) {
        return transactionManager.doInTransaction(() -> {
            Optional<org.openkilda.model.Isl> isl = islRepository.findByEndpoints(
                    islToUpdate.getSrcSwitch().getDpId(), islToUpdate.getSrcPort(),
                    islToUpdate.getDstSwitch().getDpId(), islToUpdate.getDstPort());
            isl.ifPresent(link -> {
                link.setMaxBandwidth(link.getSpeed());
                link.setAvailableBandwidth(link.getSpeed());
                link.setDefaultMaxBandwidth(link.getSpeed());
                islRepository.createOrUpdate(link);
            });

            return isl.isPresent();
        });
    }

    /**
     * Remove all inactive switches.
     *
     * @return true if at least 1 switch was deleted
     */
    @Override
    public boolean removeInactiveSwitches() {
        return transactionManager.doInTransaction(() -> {
            //TODO(siakovenko): non optimal and a dedicated method for fetching inactive entities must be introduced.
            Collection<Switch> inactiveSwitches = switchRepository.findAll().stream()
                    .filter(isl -> isl.getStatus() != SwitchStatus.ACTIVE)
                    .collect(toList());

            inactiveSwitches.forEach(switchRepository::delete);
            return !inactiveSwitches.isEmpty();
        });
    }

    @Override
    public Switch getSwitch(SwitchId switchId) {
        return switchRepository.findById(switchId).get();
    }

    /**
     * Set cost for all ISLs to be equal to DEFAULT_COST value.
     *
     * @return true if at least 1 ISL was affected
     */
    @Override
    public boolean resetCosts() {
        Session session = ((Neo4jSessionFactory) transactionManager).getSession();
        String query = "MATCH ()-[i:isl]->() SET i.cost=$cost, i.time_unstable=null";
        Result result = session.query(query, ImmutableMap.of("cost", DEFAULT_COST));
        return result.queryStatistics().getPropertiesSet() > 0;
    }

    /**
     * Get ISL cost.
     *
     * @param islToGet ISL for which cost should be retrieved
     * @return ISL cost
     */
    @Override
    public int getIslCost(Isl islToGet) {
        Optional<org.openkilda.model.Isl> isl = islRepository.findByEndpoints(
                islToGet.getSrcSwitch().getDpId(), islToGet.getSrcPort(),
                islToGet.getDstSwitch().getDpId(), islToGet.getDstPort());
        return isl.map(org.openkilda.model.Isl::getCost)
                .filter(cost -> cost > 0)
                .orElse(DEFAULT_COST);
    }

    /**
     * Count all flow records.
     *
     * @return the number of flow records
     */
    @Override
    public int countFlows() {
        return (int) flowRepository.countFlows();
    }

    /**
     * Get all possible paths between source and destination switches.
     *
     * @param src source switch ID
     * @param dst destination switch ID
     * @return list of PathInfoData objects
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<PathInfoData> getPaths(SwitchId src, SwitchId dst) {
        //TODO(siakovenko): need to revise the tests that require path information as persistence implementation
        // may not provide an ability to find a path.
        Session session = ((Neo4jSessionFactory) transactionManager).getSession();

        String query = "match p=(:switch {name: {src_switch}})-[:isl*.." + DEFAULT_DEPTH + "]->"
                + "(:switch {name: {dst_switch}}) "
                + "WHERE ALL(x IN NODES(p) WHERE SINGLE(y IN NODES(p) WHERE y = x)) "
                + "WITH RELATIONSHIPS(p) as links, NODES(p) as nodes "
                + "WHERE ALL(l IN links WHERE l.status = 'active') "
                + "return links, nodes";
        Map<String, Object> params = new HashMap<>(2);
        params.put("src_switch", src.toString());
        params.put("dst_switch", dst.toString());

        Result result = session.query(query, params);
        List<PathInfoData> deserializedResults = new ArrayList<>();
        for (Map<String, Object> record : result.queryResults()) {
            List<PathNode> path = new ArrayList<>();
            int seqId = 0;
            for (org.openkilda.model.Isl link : (List<org.openkilda.model.Isl>) record.get("links")) {
                path.add(new PathNode(link.getSrcSwitch().getSwitchId(),
                        link.getSrcPort(), seqId++,
                        (long) link.getLatency()));
                path.add(new PathNode(link.getDestSwitch().getSwitchId(),
                        link.getDestPort(), seqId++,
                        (long) link.getLatency()));
            }
            deserializedResults.add(new PathInfoData(0, path));
        }
        return deserializedResults;
    }

    /**
     * Get flow.
     *
     * @param flowId flow ID
     * @return Flow
     */
    @Override
    public Flow getFlow(String flowId) {
        return flowRepository.findById(flowId).get();
    }

    /**
     * Get transit VLAN for a flow.
     * Pay attention: 2 transit VLANs can be returned for an old flow,
     * because system used to create two different transit VLANs for a flow.
     * Now system creates one transit VLAN for a flow.
     *
     * @param forwardPathId forward path Id
     * @param reversePathId reverse path Id
     * @return Collection of TransitVlan
     */
    @Override
    public Collection<TransitVlan> getTransitVlans(PathId forwardPathId, PathId reversePathId) {
        return transitVlanRepository.findByPathId(forwardPathId, reversePathId);
    }

    /**
     * Update flow bandwidth.
     *
     * @param flowId flow ID
     * @param newBw new bandwidth to be set
     */
    @Override
    public void updateFlowBandwidth(String flowId, long newBw) {
        Flow flow = flowRepository.findById(flowId, FetchStrategy.DIRECT_RELATIONS)
                .orElseThrow(() -> new RuntimeException(format("Unable to find Flow for %s", flowId)));
        flow.setBandwidth(newBw);
        flow.getForwardPath().setBandwidth(newBw);
        flow.getReversePath().setBandwidth(newBw);
        flowRepository.createOrUpdate(flow);
    }

    @Override
    public void updateFlowMeterId(String flowId, MeterId newMeterId) {
        //TODO(andriidovhan) rewrite it, FlowPair flowPair -> Flow
        //FlowPair flowPair = flowPairRepository.findById(flowId)
        //        .orElseThrow(() -> new RuntimeException(format("Unable to find Flow for %s", flowId)));
        //flowPair.getForward().setMeterId(newMeterId.getValue());
        //flowPair.getReverse().setMeterId(newMeterId.getValue());
        //flowRepository.createOrUpdate(flowPair);
        //flow path
        Collection<FlowPath> flowPaths = flowPathRepository.findByFlowId(flowId);
        flowPaths.forEach(p -> {
            p.setMeterId(newMeterId);
            flowPathRepository.createOrUpdate(p);
        });
    }

    @Override
    public boolean updateIslTimeUnstable(Isl isl, Instant newTimeUnstable) {
        org.openkilda.model.Isl islToUpdate = islRepository.findByEndpoints(
                isl.getSrcSwitch().getDpId(), isl.getSrcPort(),
                isl.getDstSwitch().getDpId(), isl.getDstPort()).get();
        islToUpdate.setTimeUnstable(newTimeUnstable);
        islRepository.createOrUpdate(islToUpdate);

        return islToUpdate.getTimeUnstable().equals(newTimeUnstable);
    }

    @Override
    public List<Object> dumpAllNodes() {
        Session session = ((Neo4jSessionFactory) transactionManager).getSession();
        String query = "MATCH (n) RETURN n";
        Result result = session.query(query, Collections.emptyMap());
        return Lists.newArrayList(result.queryResults()).stream()
                .map(n -> n.get("n")).collect(toList());
    }

    @Override
    public List<Map<String, Object>> dumpAllRelations() {
        Session session = ((Neo4jSessionFactory) transactionManager).getSession();
        String query = "MATCH ()-[r]->() RETURN r";
        Result result = session.query(query, Collections.emptyMap());
        return Lists.newArrayList(result.queryResults()).stream()
                .map(r -> ((RelationshipModel) r.get("r")).getPropertyList().stream()
                        .collect(toMap(Property::getKey, Property::getValue)))
                .collect(toList());
    }

    @Override
    public List<Object> dumpAllSwitches() {
        Session session = ((Neo4jSessionFactory) transactionManager).getSession();
        String query = "MATCH (s:switch) RETURN s";
        Result result = session.query(query, Collections.emptyMap());
        return Lists.newArrayList(result.queryResults()).stream()
                .map(n -> n.get("s")).collect(toList());
    }

    @Override
    public List<Object> dumpAllIsls() {
        Session session = ((Neo4jSessionFactory) transactionManager).getSession();
        String query = "MATCH ()-[i:isl]->() RETURN i";
        Result result = session.query(query, Collections.emptyMap());
        return Lists.newArrayList(result.queryResults()).stream()
                .map(r -> ((RelationshipModel) r.get("i")).getPropertyList().stream()
                        .collect(toMap(Property::getKey, Property::getValue)))
                .collect(toList());
    }

    private FlowDto convert(UnidirectionalFlow flow) {
        return flowMapper.map(flow);
    }

    private static final FlowMapper flowMapper = Mappers.getMapper(FlowMapper.class);

    @Mapper
    public interface FlowMapper {
        @Mapping(source = "srcPort", target = "sourcePort")
        @Mapping(source = "srcVlan", target = "sourceVlan")
        @Mapping(source = "destPort", target = "destinationPort")
        @Mapping(source = "destVlan", target = "destinationVlan")
        @Mapping(target = "sourceSwitch", expression = "java(flow.getSrcSwitch().getSwitchId())")
        @Mapping(target = "destinationSwitch", expression = "java(flow.getDestSwitch().getSwitchId())")
        @Mapping(source = "status", target = "state")
        FlowDto map(UnidirectionalFlow flow);

        /**
         * Convert {@link Instant} to {@link String}.
         */
        default String map(Instant time) {
            if (time == null) {
                return null;
            }
            return DateTimeFormatter.ISO_INSTANT.format(time);
        }
    }
}
