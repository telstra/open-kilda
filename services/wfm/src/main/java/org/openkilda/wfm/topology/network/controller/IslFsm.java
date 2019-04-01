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

package org.openkilda.wfm.topology.network.controller;

import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Isl;
import org.openkilda.model.Isl.IslBuilder;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.controller.IslFsm.IslFsmContext;
import org.openkilda.wfm.topology.network.controller.IslFsm.IslFsmEvent;
import org.openkilda.wfm.topology.network.controller.IslFsm.IslFsmState;
import org.openkilda.wfm.topology.network.model.BiIslDataHolder;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.IslEndpointStatus;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.facts.DiscoveryFacts;
import org.openkilda.wfm.topology.network.service.IIslCarrier;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Slf4j
public final class IslFsm extends AbstractBaseFsm<IslFsm, IslFsmState, IslFsmEvent,
        IslFsmContext> {
    private final IslRepository islRepository;
    private final LinkPropsRepository linkPropsRepository;
    private final FlowSegmentRepository flowSegmentRepository;
    private final SwitchRepository switchRepository;
    private final TransactionManager transactionManager;
    private final FeatureTogglesRepository featureTogglesRepository;

    private final int costRaiseOnPhysicalDown;
    private final int islCostWhenUnderMaintenance;
    private final BiIslDataHolder<IslEndpointStatus> endpointStatus;

    private final DiscoveryFacts discoveryFacts;

    private static final StateMachineBuilder<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> builder;

    private final NetworkTopologyDashboardLogger logWrapper = new NetworkTopologyDashboardLogger(log);

    static {
        builder = StateMachineBuilderFactory.create(
                IslFsm.class, IslFsmState.class, IslFsmEvent.class, IslFsmContext.class,
                // extra parameters
                PersistenceManager.class, NetworkOptions.class, IslReference.class);

        String updateEndpointStatusMethod = "updateEndpointStatus";
        String updateAndPersistEndpointStatusMethod = "updateAndPersistEndpointStatus";

        // INIT
        builder.transition()
                .from(IslFsmState.INIT).to(IslFsmState.DOWN).on(IslFsmEvent.ISL_UP)
                .callMethod("handleInitialDiscovery");
        builder.transition()
                .from(IslFsmState.INIT).to(IslFsmState.DOWN).on(IslFsmEvent.ISL_DOWN)
                .callMethod(updateAndPersistEndpointStatusMethod);
        builder.transition()
                .from(IslFsmState.INIT).to(IslFsmState.MOVED).on(IslFsmEvent.ISL_MOVE)
                .callMethod(updateAndPersistEndpointStatusMethod);
        builder.transition()
                .from(IslFsmState.INIT).to(IslFsmState.DOWN).on(IslFsmEvent._HISTORY_DOWN);
        builder.transition()
                .from(IslFsmState.INIT).to(IslFsmState.UP).on(IslFsmEvent._HISTORY_UP);
        builder.transition()
                .from(IslFsmState.INIT).to(IslFsmState.MOVED).on(IslFsmEvent._HISTORY_MOVED);
        builder.internalTransition()
                .within(IslFsmState.INIT).on(IslFsmEvent.HISTORY)
                .callMethod("handleHistory");

        // DOWN
        builder.transition()
                .from(IslFsmState.DOWN).to(IslFsmState.UP_ATTEMPT).on(IslFsmEvent.ISL_UP)
                .callMethod(updateEndpointStatusMethod);
        builder.transition()
                .from(IslFsmState.DOWN).to(IslFsmState.MOVED).on(IslFsmEvent.ISL_MOVE)
                .callMethod(updateEndpointStatusMethod);
        builder.internalTransition()
                .within(IslFsmState.DOWN).on(IslFsmEvent.ISL_DOWN)
                .callMethod(updateAndPersistEndpointStatusMethod);
        builder.internalTransition()
                .within(IslFsmState.DOWN).on(IslFsmEvent.ISL_REMOVE)
                .callMethod("removeAttempt");
        builder.transition()
                .from(IslFsmState.DOWN).to(IslFsmState.DELETED).on(IslFsmEvent._ISL_REMOVE_SUCESS);
        builder.onEntry(IslFsmState.DOWN)
                .callMethod("downEnter");

        // UP_ATTEMPT
        builder.transition()
                .from(IslFsmState.UP_ATTEMPT).to(IslFsmState.DOWN).on(IslFsmEvent._UP_ATTEMPT_FAIL);
        builder.transition()
                .from(IslFsmState.UP_ATTEMPT).to(IslFsmState.UP).on(IslFsmEvent._UP_ATTEMPT_SUCCESS);
        builder.onEntry(IslFsmState.UP_ATTEMPT)
                .callMethod("handleUpAttempt");

        // UP
        builder.transition()
                .from(IslFsmState.UP).to(IslFsmState.DOWN).on(IslFsmEvent.ISL_DOWN);
        builder.transition()
                .from(IslFsmState.UP).to(IslFsmState.MOVED).on(IslFsmEvent.ISL_MOVE);
        builder.internalTransition().within(IslFsmState.UP).on(IslFsmEvent.BFD_UPDATE)
                .callMethod("handleBfdEnableDisable");
        builder.onEntry(IslFsmState.UP)
                .callMethod("upEnter");
        builder.onExit(IslFsmState.UP)
                .callMethod("upExit");

        // MOVED
        builder.transition()
                .from(IslFsmState.MOVED).to(IslFsmState.UP_ATTEMPT).on(IslFsmEvent.ISL_UP)
                .callMethod(updateEndpointStatusMethod);
        builder.internalTransition()
                .within(IslFsmState.MOVED).on(IslFsmEvent.ISL_DOWN)
                .callMethod(updateAndPersistEndpointStatusMethod);
        builder.internalTransition()
                .within(IslFsmState.MOVED).on(IslFsmEvent.ISL_REMOVE)
                .callMethod("removeAttempt");
        builder.transition()
                .from(IslFsmState.MOVED).to(IslFsmState.DELETED).on(IslFsmEvent._ISL_REMOVE_SUCESS);
        builder.onEntry(IslFsmState.MOVED)
                .callMethod("movedEnter");

        // DELETED
        builder.defineFinalState(IslFsmState.DELETED);
    }

    public static FsmExecutor<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> makeExecutor() {
        return new FsmExecutor<>(IslFsmEvent.NEXT);
    }

    /**
     * Create and properly initialize new {@link IslFsm}.
     */
    public static IslFsm create(PersistenceManager persistenceManager, NetworkOptions options,
                                IslReference reference) {
        IslFsm fsm = builder.newStateMachine(IslFsmState.INIT, persistenceManager, options, reference);
        fsm.start();
        return fsm;
    }

    public IslFsm(PersistenceManager persistenceManager, NetworkOptions options, IslReference reference) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islRepository = repositoryFactory.createIslRepository();
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();

        transactionManager = persistenceManager.getTransactionManager();

        costRaiseOnPhysicalDown = options.getIslCostRaiseOnPhysicalDown();
        islCostWhenUnderMaintenance = options.getIslCostWhenUnderMaintenance();
        endpointStatus = new BiIslDataHolder<>(reference);
        endpointStatus.putBoth(new IslEndpointStatus(IslEndpointStatus.Status.DOWN));

        discoveryFacts = new DiscoveryFacts(reference);
    }

    // -- FSM actions --

    public void handleHistory(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        applyHistory(context.getHistory());

        IslFsmEvent route;
        IslEndpointStatus.Status status = getAggregatedStatus();
        switch (status) {
            case UP:
                route = IslFsmEvent._HISTORY_UP;
                break;
            case DOWN:
                route = IslFsmEvent._HISTORY_DOWN;
                break;
            case MOVED:
                route = IslFsmEvent._HISTORY_MOVED;
                break;
            default:
                throw new IllegalArgumentException(makeInvalidMappingMessage(
                        status.getClass(), IslFsmEvent.class, status));
        }

        fire(route, context);
    }

    public void handleInitialDiscovery(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        discoveryFacts.put(context.getEndpoint(), context.getIslData());
        updateEndpointStatusByEvent(event, context);
        saveStatusTransaction();
    }

    public void updateEndpointStatus(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        updateEndpointStatusByEvent(event, context);
    }

    public void updateAndPersistEndpointStatus(IslFsmState from, IslFsmState to, IslFsmEvent event,
                                               IslFsmContext context) {
        updateEndpointStatusByEvent(event, context);
        saveStatusTransaction();
    }

    public void downEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);
        saveStatusTransaction();
    }

    public void handleUpAttempt(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        discoveryFacts.put(context.getEndpoint(), context.getIslData());

        IslFsmEvent route;
        if (getAggregatedStatus() == IslEndpointStatus.Status.UP) {
            route = IslFsmEvent._UP_ATTEMPT_SUCCESS;
        } else {
            route = IslFsmEvent._UP_ATTEMPT_FAIL;
        }
        fire(route, context);
    }

    public void upEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);
        logWrapper.onIslUpdateStatus(discoveryFacts.getReference(), to.toString());
        saveAllTransaction();

        if (event != IslFsmEvent._HISTORY_UP) {
            // Do not produce reroute during recovery system state from DB
            triggerDownFlowReroute(context);
        }

        if (shouldSetupBfd()) {
            emitBfdEnableRequest(context);
        }
    }

    public void upExit(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} is no more UP (reason:{})",
                  discoveryFacts.getReference(), context.getDownReason());

        // FIXME(surabujin): extract logging logic into separate(nested) FSM
        String nextState = "Unknown";
        if (event == IslFsmEvent.ISL_DOWN) {
            nextState = IslFsmState.DOWN.toString();
        } else if (event == IslFsmEvent.ISL_MOVE) {
            nextState = IslFsmState.MOVED.toString();
        }

        logWrapper.onIslUpdateStatus(discoveryFacts.getReference(), nextState);

        updateEndpointStatusByEvent(event, context);
        saveStatusAndCostRaiseTransaction(context);
        triggerAffectedFlowReroute(context);
    }

    public void movedEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);
        saveStatusTransaction();
    }

    public void handleBfdEnableDisable(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        if (context.getBfdEnable()) {
            emitBfdEnableRequest(context);
        } else {
            emitBfdDisableRequest(context);
        }
    }

    public void removeAttempt(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        // FIXME(surabujin): this check is always true, because it is called from DOWN or MOVED state
        if (getAggregatedStatus() != IslEndpointStatus.Status.UP) {
            fire(IslFsmEvent._ISL_REMOVE_SUCESS);
        }
    }

    // -- private/service methods --

    private void applyHistory(Isl history) {
        Endpoint source = Endpoint.of(history.getSrcSwitch().getSwitchId(), history.getSrcPort());
        Endpoint dest = Endpoint.of(history.getDestSwitch().getSwitchId(), history.getDestPort());
        transactionManager.doInTransaction(() -> {
            loadPersistentData(source, dest);
            loadPersistentData(dest, source);
        });
    }

    private void updateEndpointStatusByEvent(IslFsmEvent event, IslFsmContext context) {
        IslEndpointStatus status;
        switch (event) {
            case ISL_UP:
                status = new IslEndpointStatus(IslEndpointStatus.Status.UP);
                break;
            case ISL_DOWN:
                status = new IslEndpointStatus(IslEndpointStatus.Status.DOWN, context.getDownReason());
                break;
            case ISL_MOVE:
                status = new IslEndpointStatus(IslEndpointStatus.Status.MOVED);
                break;
            default:
                throw new IllegalStateException(String.format("Unexpected event %s for %s.handleSourceDestUpState",
                                                              event, getClass().getName()));
        }
        endpointStatus.put(context.getEndpoint(), status);
    }

    private void loadPersistentData(Endpoint start, Endpoint end) {
        Optional<Isl> potentialIsl = islRepository.findByEndpoints(
                start.getDatapath(), start.getPortNumber(),
                end.getDatapath(), end.getPortNumber());
        if (potentialIsl.isPresent()) {
            Isl isl = potentialIsl.get();
            Endpoint endpoint = Endpoint.of(isl.getDestSwitch().getSwitchId(), isl.getDestPort());

            IslEndpointStatus status = new IslEndpointStatus(mapStatus(isl.getStatus()), isl.getDownReason());
            endpointStatus.put(endpoint, status);
            discoveryFacts.put(endpoint, new IslDataHolder(isl));
        } else {
            log.error("There is no persistent ISL data {} ==> {} (possible race condition during topology "
                              + "initialisation)", start, end);
        }
    }

    private void triggerAffectedFlowReroute(IslFsmContext context) {
        Endpoint source = discoveryFacts.getReference().getSource();

        String reason = makeRerouteReason(context.getEndpoint(), context.getDownReason());

        // FIXME (surabujin): why do we send only one ISL endpoint here?
        PathNode pathNode = new PathNode(source.getDatapath(), source.getPortNumber(), 0);
        RerouteAffectedFlows trigger = new RerouteAffectedFlows(pathNode, reason);
        context.getOutput().triggerReroute(trigger);
    }

    private void triggerDownFlowReroute(IslFsmContext context) {
        if (shouldEmitDownFlowReroute()) {
            RerouteInactiveFlows trigger = new RerouteInactiveFlows(String.format(
                    "ISL %s status become %s", discoveryFacts.getReference(), IslStatus.ACTIVE));
            context.getOutput().triggerReroute(trigger);
        }
    }

    private void emitBfdEnableRequest(IslFsmContext context) {
        IslReference reference = discoveryFacts.getReference();
        context.getOutput().bfdEnableRequest(reference.getSource(), reference);
        context.getOutput().bfdEnableRequest(reference.getDest(), reference);
    }

    private void emitBfdDisableRequest(IslFsmContext context) {
        IslReference reference = discoveryFacts.getReference();
        context.getOutput().bfdDisableRequest(reference.getSource());
        context.getOutput().bfdDisableRequest(reference.getDest());
    }

    private void saveAllTransaction() {
        try {
            transactionManager.doInTransaction(() -> saveAll(Instant.now()));
        } catch (Exception e) {
            logDbException(e);
            throw e;
        }
    }

    private void saveStatusTransaction() {
        try {
            transactionManager.doInTransaction(() -> saveStatus(Instant.now()));
        } catch (Exception e) {
            logDbException(e);
            throw e;
        }
    }

    private void saveStatusAndCostRaiseTransaction(IslFsmContext context) {
        try {
            transactionManager.doInTransaction(() -> {
                Instant timeNow = Instant.now();

                saveStatus(timeNow);
                if (IslDownReason.PORT_DOWN == context.getDownReason()) {
                    raiseCostOnPhysicalDown(timeNow);
                }
            });
        } catch (Exception e) {
            logDbException(e);
            throw e;
        }
    }

    private void saveAll(Instant timeNow) {
        Socket socket = prepareSocket();
        saveAll(socket.getSource(), socket.getDest(), timeNow, endpointStatus.getForward());
        saveAll(socket.getDest(), socket.getSource(), timeNow, endpointStatus.getReverse());
    }

    private void saveAll(Anchor source, Anchor dest, Instant timeNow, IslEndpointStatus endpointData) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        link.setTimeModify(timeNow);

        applyIslGenericData(link);
        applyIslMaxBandwidth(link, source.getEndpoint(), dest.getEndpoint());
        applyIslAvailableBandwidth(link, source.getEndpoint(), dest.getEndpoint());
        applyIslStatus(link, endpointData, timeNow);

        pushIslChanges(link);
    }

    private void saveStatus(Instant timeNow) {
        Socket socket = prepareSocket();
        saveStatus(socket.getSource(), socket.getDest(), timeNow, endpointStatus.getForward());
        saveStatus(socket.getDest(), socket.getSource(), timeNow, endpointStatus.getReverse());
    }

    private void saveStatus(Anchor source, Anchor dest, Instant timeNow, IslEndpointStatus endpointData) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        applyIslStatus(link, endpointData, timeNow);
        pushIslChanges(link);
    }

    private void raiseCostOnPhysicalDown(Instant timeNow) {
        Socket socket = prepareSocket();
        raiseCostOnPhysicalDown(socket.getSource(), socket.getDest(), timeNow);
        raiseCostOnPhysicalDown(socket.getDest(), socket.getSource(), timeNow);
    }

    private void raiseCostOnPhysicalDown(Anchor source, Anchor dest, Instant timeNow) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        log.debug("Raise ISL {} ===> {} cost due to physical down (cost-now:{}, raise:{})",
                  source, dest, link.getCost(), costRaiseOnPhysicalDown);
        applyIslCostRaiseOnPhysicalDown(link, timeNow);
        pushIslChanges(link);
    }

    private Socket prepareSocket() {
        IslReference reference = discoveryFacts.getReference();
        Anchor source = loadSwitchCreateIfMissing(reference.getSource());
        Anchor dest = loadSwitchCreateIfMissing(reference.getDest());
        switchRepository.lockSwitches(source.getSw(), dest.getSw());

        return new Socket(source, dest);
    }

    private Isl loadOrCreateIsl(Anchor source, Anchor dest, Instant timeNow) {
        return loadIsl(source.getEndpoint(), dest.getEndpoint())
                .orElseGet(() -> createIsl(source, dest, timeNow));
    }

    private Isl createIsl(Anchor source, Anchor dest, Instant timeNow) {
        final Endpoint sourceEndpoint = source.getEndpoint();
        final Endpoint destEndpoint = dest.getEndpoint();
        IslBuilder islBuilder = Isl.builder()
                .timeCreate(timeNow)
                .timeModify(timeNow)
                .srcSwitch(source.getSw())
                .srcPort(sourceEndpoint.getPortNumber())
                .destSwitch(dest.getSw())
                .destPort(destEndpoint.getPortNumber())
                .underMaintenance(source.getSw().isUnderMaintenance() || dest.getSw().isUnderMaintenance());
        initializeFromLinkProps(sourceEndpoint, destEndpoint, islBuilder);
        Isl link = islBuilder.build();

        if (link.isUnderMaintenance()) {
            link.setCost(link.getCost() + islCostWhenUnderMaintenance);
        }

        log.debug("Create new DB object (prefilled): {}", link);
        return link;
    }

    private Anchor loadSwitchCreateIfMissing(Endpoint endpoint) {
        final SwitchId datapath = endpoint.getDatapath();
        Switch sw = switchRepository.findById(datapath)
                .orElseGet(() -> {
                    log.error("Switch {} is missing in DB, create empty switch record", datapath);
                    return createSwitch(datapath);
                });
        return new Anchor(endpoint, sw);
    }

    private Switch createSwitch(SwitchId datapath) {
        Switch sw = Switch.builder()
                .switchId(datapath)
                .status(SwitchStatus.INACTIVE)
                .description(String.format("auto created as part of ISL %s discovery", discoveryFacts.getReference()))
                .build();

        switchRepository.createOrUpdate(sw);

        return sw;
    }

    private Optional<Isl> loadIsl(Endpoint source, Endpoint dest) {
        return islRepository.findByEndpoints(
                source.getDatapath(), source.getPortNumber(),
                dest.getDatapath(), dest.getPortNumber())
                .map(link -> {
                    log.debug("Read ISL object: {}", link);
                    return link;
                });
    }

    private void applyIslGenericData(Isl link) {
        IslDataHolder aggData = discoveryFacts.makeAggregatedData();
        if (aggData != null) {
            link.setSpeed(aggData.getSpeed());
            link.setLatency(aggData.getLatency());
            link.setMaxBandwidth(aggData.getMaximumBandwidth());
            link.setDefaultMaxBandwidth(aggData.getMaximumBandwidth());
        }
    }

    private void applyIslStatus(Isl link, IslEndpointStatus endpointData, Instant timeNow) {
        IslStatus become = mapStatus(endpointData.getStatus());
        IslStatus aggStatus = mapStatus(getAggregatedStatus());
        if (link.getActualStatus() != become || link.getStatus() != aggStatus) {
            link.setTimeModify(timeNow);

            link.setActualStatus(become);
            link.setStatus(aggStatus);
            link.setDownReason(endpointData.getDownReason());
        }
    }

    private void applyIslMaxBandwidth(Isl link, Endpoint source, Endpoint dest) {
        loadLinkProps(source, dest)
                .ifPresent(props -> applyIslMaxBandwidth(link, props));
    }

    private void applyIslMaxBandwidth(Isl link, LinkProps props) {
        Long maxBandwidth = props.getMaxBandwidth();
        if (maxBandwidth != null) {
            link.setMaxBandwidth(maxBandwidth);
        }
    }

    private void applyIslAvailableBandwidth(Isl link, Endpoint source, Endpoint dest) {
        IslDataHolder dataHolder = discoveryFacts.get(source);
        if (dataHolder == null) {
            throw new IllegalStateException(String.format(
                    "There is no ISL data available for %s, unable to calculate available_bandwidth",
                    discoveryFacts.getReference()));
        }

        long maximumBandwidth = dataHolder.getMaximumBandwidth();
        long usedBandwidth = flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                source.getDatapath(), source.getPortNumber(),
                dest.getDatapath(), dest.getPortNumber());
        link.setAvailableBandwidth(maximumBandwidth - usedBandwidth);
    }

    private void applyIslCostRaiseOnPhysicalDown(Isl link, Instant timeNow) {
        if (link.getCost() < costRaiseOnPhysicalDown) {
            link.setTimeModify(timeNow);
            link.setCost(link.getCost() + costRaiseOnPhysicalDown);
        }
    }

    private void initializeFromLinkProps(Endpoint source, Endpoint dest, IslBuilder isl) {
        Optional<LinkProps> linkProps = loadLinkProps(source, dest);
        if (linkProps.isPresent()) {
            LinkProps entry = linkProps.get();

            Integer cost = entry.getCost();
            if (cost != null) {
                isl.cost(cost);
            }

            Long maxBandwidth = entry.getMaxBandwidth();
            if (maxBandwidth != null) {
                isl.maxBandwidth(maxBandwidth);
            }
        }
    }

    private void pushIslChanges(Isl link) {
        log.debug("Write ISL object: {}", link);
        islRepository.createOrUpdate(link);
    }

    private Optional<LinkProps> loadLinkProps(Endpoint source, Endpoint dest) {
        Collection<LinkProps> storedProps = linkPropsRepository.findByEndpoints(
                source.getDatapath(), source.getPortNumber(),
                dest.getDatapath(), dest.getPortNumber());
        Optional<LinkProps> result = Optional.empty();
        for (LinkProps entry : storedProps) {
            result = Optional.of(entry);
            // We can/should put "break" here but it lead to warnings... Anyway only one match possible
            // by such(full) query so we can avoid "break" here.
        }
        return result;
    }

    private IslEndpointStatus.Status getAggregatedStatus() {
        IslEndpointStatus.Status forward = endpointStatus.getForward().getStatus();
        IslEndpointStatus.Status reverse = endpointStatus.getReverse().getStatus();
        if (forward == reverse) {
            return forward;
        }

        if (forward == IslEndpointStatus.Status.MOVED || reverse == IslEndpointStatus.Status.MOVED) {
            return IslEndpointStatus.Status.MOVED;
        }

        return IslEndpointStatus.Status.DOWN;
    }

    private boolean shouldSetupBfd() {
        // TODO(surabujin): ensure the switch is BFD capable

        IslReference reference = discoveryFacts.getReference();
        return isPerIslBfdToggleEnabled(reference.getSource(), reference.getDest())
                || isPerIslBfdToggleEnabled(reference.getDest(), reference.getSource());
    }

    private boolean isPerIslBfdToggleEnabled(Endpoint source, Endpoint dest) {
        return loadIsl(source, dest)
                .map(Isl::isEnableBfd)
                .orElseThrow(() -> new PersistenceException(
                        String.format("Isl %s ===> %s record not found in DB", source, dest)));
    }

    // TODO(surabujin): should this check been moved into reroute topology?
    private boolean shouldEmitDownFlowReroute() {
        return featureTogglesRepository.find()
                .map(FeatureToggles::getFlowsRerouteOnIslDiscoveryEnabled)
                .orElse(FeatureToggles.DEFAULTS.getFlowsRerouteOnIslDiscoveryEnabled());
    }

    private String makeRerouteReason(Endpoint endpoint, IslDownReason reason) {
        IslStatus status = mapStatus(getAggregatedStatus());
        IslReference reference = discoveryFacts.getReference();
        if (reason == null) {
            return String.format("ISL %s status become %s", reference, status);
        }

        String humanReason;
        switch (reason) {
            case PORT_DOWN:
                humanReason = String.format("ISL %s become %s due to physical link DOWN event on %s",
                                            reference, status, endpoint);
                break;
            case POLL_TIMEOUT:
                humanReason = String.format("ISL %s become %s because of FAIL TIMEOUT (endpoint:%s)",
                                            reference, status, endpoint);
                break;
            case BFD_DOWN:
                humanReason = String.format("ISL %s become %s because BFD detect link failure (endpoint:%s)",
                                            reference, status, endpoint);
                break;

            default:
                humanReason = String.format("ISL %s become %s (endpoint:%s, reason:%s)",
                                            reference, status, endpoint, reason);
        }

        return humanReason;
    }

    private void logDbException(Exception e) {
        log.error(
                String.format("Error in DB transaction for ISL %s: %s", discoveryFacts.getReference(), e.getMessage()),
                e);
    }

    private static IslStatus mapStatus(IslEndpointStatus.Status status) {
        switch (status) {
            case UP:
                return IslStatus.ACTIVE;
            case DOWN:
                return IslStatus.INACTIVE;
            case MOVED:
                return IslStatus.MOVED;
            default:
                throw new IllegalArgumentException(
                        makeInvalidMappingMessage(IslEndpointStatus.Status.class, IslStatus.class, status));
        }
    }

    private static IslEndpointStatus.Status mapStatus(IslStatus status) {
        switch (status) {
            case ACTIVE:
                return IslEndpointStatus.Status.UP;
            case INACTIVE:
                return IslEndpointStatus.Status.DOWN;
            case MOVED:
                return IslEndpointStatus.Status.MOVED;
            default:
                throw new IllegalArgumentException(
                        makeInvalidMappingMessage(IslStatus.class, IslEndpointStatus.Status.class, status));
        }
    }

    private static String makeInvalidMappingMessage(Class<?> from, Class<?> to, Object value) {
        return String.format("There is no mapping defined between %s and %s for %s", from.getName(),
                             to.getName(), value);
    }

    @Value
    private static class Anchor {
        Endpoint endpoint;
        Switch sw;
    }

    @Value
    private static class Socket {
        Anchor source;
        Anchor dest;
    }

    @Value
    @Builder
    public static class IslFsmContext {
        private final IIslCarrier output;
        private final Endpoint endpoint;

        private Isl history;
        private IslDataHolder islData;

        private IslDownReason downReason;

        private Boolean bfdEnable;

        /**
         * .
         */
        public static IslFsmContextBuilder builder(IIslCarrier output, Endpoint endpoint) {
            return new IslFsmContextBuilder()
                    .output(output)
                    .endpoint(endpoint);
        }
    }

    public enum IslFsmEvent {
        NEXT,

        BFD_UPDATE,

        HISTORY, _HISTORY_DOWN, _HISTORY_UP, _HISTORY_MOVED,
        ISL_UP, ISL_DOWN, ISL_MOVE,
        _UP_ATTEMPT_SUCCESS, ISL_REMOVE, _ISL_REMOVE_SUCESS, _UP_ATTEMPT_FAIL
    }

    public enum IslFsmState {
        INIT,
        UP, DOWN,
        MOVED,

        DELETED, UP_ATTEMPT
    }
}
