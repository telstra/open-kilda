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
import static org.openkilda.testing.Constants.DEFAULT_COST;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMeter;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.ferma.frames.IslFrame;
import org.openkilda.persistence.ferma.frames.SwitchFrame;
import org.openkilda.persistence.orientdb.OrientDbContextExtension;
import org.openkilda.persistence.orientdb.OrientDbPersistenceImplementation;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;

import com.syncleus.ferma.FramedGraph;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ImmutablePath;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class DatabaseSupportImpl implements Database {
    private static final int DEFAULT_DEPTH = 7;

    private final OrientDbPersistenceImplementation orientPersistenceImplementation;

    private final TransactionManager transactionManager;
    private final TransactionManager historyTransactionManager;
    private final IslRepository islRepository;
    private final SwitchRepository switchRepository;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final FlowMeterRepository flowMeterRepository;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final TransitVlanRepository transitVlanRepository;
    private final SwitchConnectedDeviceRepository switchDevicesRepository;
    private final FlowEventRepository flowEventRepository;

    public DatabaseSupportImpl(PersistenceManager persistenceManager) {
        this.orientPersistenceImplementation = persistenceManager.getImplementation(
                OrientDbPersistenceImplementation.class);

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        flowMeterRepository = repositoryFactory.createFlowMeterRepository();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();
        switchDevicesRepository = repositoryFactory.createSwitchConnectedDeviceRepository();
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        flowMirrorPointsRepository = repositoryFactory.createFlowMirrorPointsRepository();

        this.transactionManager = persistenceManager.getTransactionManager();
        historyTransactionManager = persistenceManager.getTransactionManager(flowEventRepository);
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
            });

            return isl.isPresent();
        });
    }

    @Override
    public boolean updateIslLatency(Isl islToUpdate, long latency) {
        return transactionManager.doInTransaction(() -> {
            Optional<org.openkilda.model.Isl> isl = islRepository.findByEndpoints(
                    islToUpdate.getSrcSwitch().getDpId(), islToUpdate.getSrcPort(),
                    islToUpdate.getDstSwitch().getDpId(), islToUpdate.getDstPort());
            isl.ifPresent(link -> {
                link.setLatency(latency);
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

            inactiveSwitches.forEach(switchRepository::remove);
            return !inactiveSwitches.isEmpty();
        });
    }

    @Override
    public Switch getSwitch(SwitchId switchId) {
        return transactionManager.doInTransaction(() -> switchRepository.findById(switchId)
                .map(Switch::new)
                .orElseThrow(() -> new IllegalStateException(format("Switch %s not found", switchId))));
    }

    @Override
    public void setSwitchFeatures(SwitchId switchId, Set<SwitchFeature> switchFeatures) {
        transactionManager.doInTransaction(() -> {
            Switch sw = switchRepository.findById(switchId)
                    .orElseThrow(() -> new IllegalStateException(format("Switch %s not found", switchId)));
            sw.setFeatures(switchFeatures);
        });
    }

    @Override
    public void setSwitchStatus(SwitchId switchId, SwitchStatus swStatus) {
        transactionManager.doInTransaction(() -> {
            Switch sw = switchRepository.findById(switchId)
                    .orElseThrow(() -> new IllegalStateException(format("Switch %s not found", switchId)));
            sw.setStatus(swStatus);
        });
    }

    /**
     * Set cost for all ISLs to be equal to DEFAULT_COST value, remove timeUnstable.
     *
     * @return true if at least 1 ISL was affected
     */
    @Override
    public boolean resetCosts() {
        return transactionManager.doInTransaction(() -> {
            Collection<org.openkilda.model.Isl> allIsls = islRepository.findAll();
            allIsls.forEach(isl -> {
                isl.setCost(DEFAULT_COST);
                isl.setTimeUnstable(null);
            });
            return allIsls.size() > 0;
        });
    }

    /**
     * Set cost for passed ISLs to be equal to DEFAULT_COST value, remove timeUnstable.
     *
     * @return true if at least 1 ISL was affected
     */
    @Override
    public boolean resetCosts(List<Isl> isls) {
        return transactionManager.doInTransaction(() -> {
            Collection<org.openkilda.model.Isl> dbIsls = getIsls(isls);
            dbIsls.forEach(isl -> {
                isl.setCost(DEFAULT_COST);
                isl.setTimeUnstable(null);
            });
            return dbIsls.size() > 0;
        });
    }

    /**
     * Get ISL cost.
     *
     * @param islToGet ISL for which cost should be retrieved
     * @return ISL cost
     */
    @Override
    public int getIslCost(Isl islToGet) {
        return transactionManager.doInTransaction(() -> {
            Optional<org.openkilda.model.Isl> isl = islRepository.findByEndpoints(
                    islToGet.getSrcSwitch().getDpId(), islToGet.getSrcPort(),
                    islToGet.getDstSwitch().getDpId(), islToGet.getDstPort());
            return isl.map(org.openkilda.model.Isl::getCost)
                    .filter(cost -> cost > 0)
                    .orElse(DEFAULT_COST);
        });
    }

    /**
     * Count all flow records.
     *
     * @return the number of flow records
     */
    @Override
    public int countFlows() {
        return transactionManager.doInTransaction(() -> (int) flowRepository.countFlows());
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
        return transactionManager.doInTransaction(() -> {
            FramedGraph framedGraph = getGraph();
            GraphTraversal<?, ?> rawTraversal = framedGraph.traverse(input -> input
                    .V().hasLabel(SwitchFrame.FRAME_LABEL).has(SwitchFrame.SWITCH_ID_PROPERTY, src.toString())
                    .repeat(__.outE(IslFrame.FRAME_LABEL).has(IslFrame.STATUS_PROPERTY, "active")
                            .inV().hasLabel(SwitchFrame.FRAME_LABEL)
                            .simplePath())
                    .until(__.has(SwitchFrame.SWITCH_ID_PROPERTY, dst.toString())
                            .or().loops().is(DEFAULT_DEPTH))
                    .has(SwitchFrame.SWITCH_ID_PROPERTY, dst.toString())
                    .path()
            ).getRawTraversal();

            List<PathInfoData> deserializedResults = new ArrayList<>();
            while (rawTraversal.hasNext()) {
                ImmutablePath tpPath = (ImmutablePath) rawTraversal.next();
                List<PathNode> resultPath = new ArrayList<>();
                int seqId = 0;
                for (Object hop : tpPath) {
                    if (hop instanceof Edge) {
                        Edge edge = (Edge) hop;
                        Vertex srcVertex = edge.outVertex();
                        resultPath.add(new PathNode(
                                new SwitchId((String) srcVertex.property(SwitchFrame.SWITCH_ID_PROPERTY).value()),
                                (Integer) edge.property(IslFrame.SRC_PORT_PROPERTY).value(), seqId++,
                                (Long) edge.property(IslFrame.LATENCY_PROPERTY).value()));

                        Vertex dstVertex = edge.inVertex();
                        resultPath.add(new PathNode(
                                new SwitchId((String) dstVertex.property(SwitchFrame.SWITCH_ID_PROPERTY).value()),
                                (Integer) edge.property(IslFrame.DST_PORT_PROPERTY).value(), seqId++,
                                (Long) edge.property(IslFrame.LATENCY_PROPERTY).value()));
                    }
                }
                deserializedResults.add(new PathInfoData(0, resultPath));
            }
            return deserializedResults;
        });
    }

    @Override
    public void removeConnectedDevices(SwitchId sw) {
        transactionManager.doInTransaction(() ->
                switchDevicesRepository.findBySwitchId(sw).forEach(switchDevicesRepository::remove));
    }

    /**
     * Get flow.
     *
     * @param flowId flow ID
     * @return Flow
     */
    @Override
    public Flow getFlow(String flowId) {
        return transactionManager.doInTransaction(() -> flowRepository.findById(flowId)
                .map(Flow::new)
                .orElseThrow(() -> new IllegalStateException(format("Flow %s not found", flowId))));
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
        return transactionManager.doInTransaction(() ->
                transitVlanRepository.findByPathId(forwardPathId, reversePathId).stream()
                        .map(TransitVlan::new).collect(toList()));
    }

    @Override
    public Optional<TransitVlan> getTransitVlan(PathId pathId) {
        return transactionManager.doInTransaction(() -> transitVlanRepository.findByPathId(pathId)
                .map(TransitVlan::new));
    }

    /**
     * Update flow bandwidth.
     *
     * @param flowId flow ID
     * @param newBw new bandwidth to be set
     */
    @Override
    public void updateFlowBandwidth(String flowId, long newBw) {
        transactionManager.doInTransaction(() -> {
            Flow flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new RuntimeException(format("Unable to find Flow for %s", flowId)));
            flow.setBandwidth(newBw);
            flow.getForwardPath().setBandwidth(newBw);
            flow.getReversePath().setBandwidth(newBw);
        });
    }

    @Override
    public void updateFlowMeterId(String flowId, long newMeterId) {
        //TODO(andriidovhan) rewrite it, FlowPair flowPair -> Flow
        //FlowPair flowPair = flowPairRepository.findById(flowId)
        //        .orElseThrow(() -> new RuntimeException(format("Unable to find Flow for %s", flowId)));
        //flowPair.getForward().setMeterId(newMeterId.getValue());
        //flowPair.getReverse().setMeterId(newMeterId.getValue());
        //flowRepository.createOrUpdate(flowPair);
        //flow path
        transactionManager.doInTransaction(() -> {
            Collection<FlowPath> flowPaths = flowPathRepository.findByFlowId(flowId);
            long meterId = newMeterId;
            for (FlowPath flowPath : flowPaths) {
                flowPath.setMeterId(new MeterId(meterId));
                Optional<FlowMeter> meter = flowMeterRepository.findByPathId(flowPath.getPathId());
                if (meter.isPresent()) {
                    meter.get().setMeterId(new MeterId(meterId++));
                }
            }
        });
    }

    @Override
    public List<FlowMirrorPoints> getMirrorPoints() {
        return transactionManager.doInTransaction(() -> new ArrayList<>(flowMirrorPointsRepository.findAll()));
    }

    @Override
    public void addFlowEvent(FlowEvent event) {
        historyTransactionManager.doInTransaction(() -> {
            flowEventRepository.add(event);
        });
    }

    @Override
    public boolean updateIslTimeUnstable(Isl isl, Instant newTimeUnstable) {
        return transactionManager.doInTransaction(() -> {
            org.openkilda.model.Isl islToUpdate = islRepository.findByEndpoints(
                    isl.getSrcSwitch().getDpId(), isl.getSrcPort(),
                    isl.getDstSwitch().getDpId(), isl.getDstPort()).get();
            islToUpdate.setTimeUnstable(newTimeUnstable);

            return islToUpdate.getTimeUnstable().equals(newTimeUnstable);
        });
    }

    @Override
    public Instant getIslTimeUnstable(Isl isl) {
        return transactionManager.doInTransaction(() -> islRepository.findByEndpoints(
                isl.getSrcSwitch().getDpId(), isl.getSrcPort(),
                isl.getDstSwitch().getDpId(), isl.getDstPort()).get().getTimeUnstable());
    }

    @Override
    public List<org.openkilda.model.Isl> getIsls(List<Isl> isls) {
        return transactionManager.doInTransaction(() -> islRepository.findAll().stream().filter(dbIsl ->
                isls.stream().flatMap(isl -> Stream.of(isl, isl.getReversed())).anyMatch(isl ->
                        (dbIsl.getSrcSwitch().getSwitchId().equals(isl.getSrcSwitch().getDpId())
                                && dbIsl.getSrcPort() == isl.getSrcPort()
                                && dbIsl.getDestSwitch().getSwitchId().equals(isl.getDstSwitch().getDpId())
                                && dbIsl.getDestPort() == isl.getDstPort()))).collect(toList()));
    }

    @Override
    public List<Object> dumpAllNodes() {
        return transactionManager.doInTransaction(() ->
                getGraph()
                        .traverse(g -> g.V()).getRawTraversal().toStream()
                        .map(v -> DetachedFactory.detach(v, true))
                        .collect(toList()));
    }

    @Override
    public List<Object> dumpAllRelations() {
        return transactionManager.doInTransaction(() ->
                getGraph()
                        .traverse(g -> g.V().inE()).getRawTraversal().toStream()
                        .map(e -> DetachedFactory.detach(e, true))
                        .collect(toList()));
    }

    @Override
    public List<Object> dumpAllSwitches() {
        return transactionManager.doInTransaction(() ->
                switchRepository.findAll().stream().map(Switch::new).collect(toList()));
    }

    @Override
    public List<Object> dumpAllIsls() {
        return transactionManager.doInTransaction(() ->
                islRepository.findAll().stream().map(org.openkilda.model.Isl::new).collect(toList()));
    }

    private FramedGraph getGraph() {
        PersistenceContext context = PersistenceContextManager.INSTANCE.getContextCreateIfMissing();
        OrientDbContextExtension contextExtension = orientPersistenceImplementation.getContextExtension(context);
        return contextExtension.getGraphCreateIfMissing();
    }
}
