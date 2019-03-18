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

package org.openkilda.wfm.topology.discovery.controller;

import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Isl;
import org.openkilda.model.Isl.IslBuilder;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
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
import org.openkilda.wfm.topology.discovery.DiscoveryTopologyDashboardLogger;
import org.openkilda.wfm.topology.discovery.controller.IslFsm.IslFsmContext;
import org.openkilda.wfm.topology.discovery.controller.IslFsm.IslFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.IslFsm.IslFsmState;
import org.openkilda.wfm.topology.discovery.model.BiIslDataHolder;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslDataHolder;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.facts.DiscoveryFacts;
import org.openkilda.wfm.topology.discovery.service.IIslCarrier;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
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
    private final BiIslDataHolder<DiscoveryEndpointStatus> endpointStatus;

    private final DiscoveryFacts discoveryFacts;

    private static final StateMachineBuilder<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> builder;

    private final DiscoveryTopologyDashboardLogger logWrapper = new DiscoveryTopologyDashboardLogger(log);

    static {
        builder = StateMachineBuilderFactory.create(
                IslFsm.class, IslFsmState.class, IslFsmEvent.class, IslFsmContext.class,
                // extra parameters
                PersistenceManager.class, DiscoveryOptions.class, IslReference.class);

        String updateEndpointStatusMethod = "updateEndpointStatus";
        String updateAndPersistEndpointStatusMethod = "updateAndPersistEndpointStatus";

        // INIT
        builder.transition()
                .from(IslFsmState.INIT).to(IslFsmState.DOWN).on(IslFsmEvent.ISL_UP)
                .callMethod(updateAndPersistEndpointStatusMethod);
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
    public static IslFsm create(PersistenceManager persistenceManager, DiscoveryOptions options,
                                IslReference reference) {
        IslFsm fsm = builder.newStateMachine(IslFsmState.INIT, persistenceManager, options, reference);
        fsm.start();
        return fsm;
    }

    public IslFsm(PersistenceManager persistenceManager, DiscoveryOptions options, IslReference reference) {
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
        endpointStatus.putBoth(DiscoveryEndpointStatus.DOWN);

        discoveryFacts = new DiscoveryFacts(reference);
    }

    // -- FSM actions --

    protected void handleHistory(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        applyHistory(context.getHistory());

        IslFsmEvent route;
        DiscoveryEndpointStatus status = getAggregatedStatus();
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

    protected void updateEndpointStatus(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        updateEndpointStatusByEvent(event, context);
    }

    protected void updateAndPersistEndpointStatus(IslFsmState from, IslFsmState to, IslFsmEvent event,
                                                  IslFsmContext context) {
        updateEndpointStatusByEvent(event, context);
        saveStatusTransaction();
    }

    protected void downEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);
        saveStatusTransaction();
    }

    protected void handleUpAttempt(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        discoveryFacts.put(context.getEndpoint(), context.getIslData());

        IslFsmEvent route;
        if (getAggregatedStatus() == DiscoveryEndpointStatus.UP) {
            route = IslFsmEvent._UP_ATTEMPT_SUCCESS;
        } else {
            route = IslFsmEvent._UP_ATTEMPT_FAIL;
        }
        fire(route, context);
    }

    protected void upEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);

        saveAllTransaction();

        if (event != IslFsmEvent._HISTORY_UP) {
            // Do not produce reroute during recovery system state from DB
            triggerDownFlowReroute(context);
        }

        if (shouldUseBfd()) {
            emitBfdEnableRequest(context);
        }
    }

    protected void upExit(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} is no more UP (physical-down:{})",
                  discoveryFacts.getReference(), to, context.getPhysicalLinkDown());

        updateEndpointStatusByEvent(event, context);
        saveStatusAndCostRaiseTransaction(context);
        triggerAffectedFlowReroute(context);
    }

    protected void movedEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);
        saveStatusTransaction();
    }

    protected void handleBfdEnableDisable(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        if (context.getBfdEnable()) {
            emitBfdEnableRequest(context);
        } else {
            emitBfdDisableRequest(context);
        }
    }

    protected void removeAttempt(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        // FIXME(surabujin): this check is always true, because it is called from DOWN or MOVED state
        if (getAggregatedStatus() != DiscoveryEndpointStatus.UP) {
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
        DiscoveryEndpointStatus status;
        switch (event) {
            case ISL_UP:
                status = DiscoveryEndpointStatus.UP;
                break;
            case ISL_DOWN:
                status = DiscoveryEndpointStatus.DOWN;
                break;
            case ISL_MOVE:
                status = DiscoveryEndpointStatus.MOVED;
                break;
            default:
                throw new IllegalStateException(String.format("Unexpected event %s for %s.handleSourceDestUpState",
                                                              event, getClass().getName()));
        }

        String message = String.format("ISL changed status to: %s", status);
        logWrapper.onIslUpdateStatus(Level.INFO, message, discoveryFacts.getReference(), status.toString());
        endpointStatus.put(context.getEndpoint(), status);
    }

    private void loadPersistentData(Endpoint start, Endpoint end) {
        Optional<Isl> potentialIsl = islRepository.findByEndpoints(
                start.getDatapath(), start.getPortNumber(),
                end.getDatapath(), end.getPortNumber());
        if (potentialIsl.isPresent()) {
            Isl isl = potentialIsl.get();
            Endpoint endpoint = Endpoint.of(isl.getDestSwitch().getSwitchId(), isl.getDestPort());
            endpointStatus.put(endpoint, mapStatus(isl.getStatus()));
            discoveryFacts.put(endpoint, new IslDataHolder(isl));
        } else {
            log.error("There is no persistent ISL data {} ==> {} (possible race condition during topology "
                              + "initialisation)", start, end);
        }
    }

    private void triggerAffectedFlowReroute(IslFsmContext context) {
        Endpoint source = discoveryFacts.getReference().getSource();

        IslStatus status = mapStatus(getAggregatedStatus());
        IslReference reference = discoveryFacts.getReference();
        String reason;
        if (context.getPhysicalLinkDown() != null && context.getPhysicalLinkDown()) {
            reason = String.format("ISL %s become %s due to physical link DOWN event on %s",
                                   reference, status, context.getEndpoint());
        } else {
            reason = String.format("ISL %s status become %s", reference, status);
        }

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
                if (context.getPhysicalLinkDown() != null && context.getPhysicalLinkDown()) {
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

    private void saveAll(Anchor source, Anchor dest, Instant timeNow, DiscoveryEndpointStatus uniStatus) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        link.setTimeModify(timeNow);

        applyIslGenericData(link);
        applyIslAvailableBandwidth(link, source.getEndpoint(), dest.getEndpoint());
        applyIslStatus(link, uniStatus, timeNow);

        pushIslChanges(link);
    }

    private void saveStatus(Instant timeNow) {
        Socket socket = prepareSocket();
        saveStatus(socket.getSource(), socket.getDest(), timeNow, endpointStatus.getForward());
        saveStatus(socket.getDest(), socket.getSource(), timeNow, endpointStatus.getReverse());
    }

    private void saveStatus(Anchor source, Anchor dest, Instant timeNow, DiscoveryEndpointStatus uniStatus) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        applyIslStatus(link, uniStatus, timeNow);
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
        Anchor source = loadSwitch(reference.getSource());
        Anchor dest = loadSwitch(reference.getDest());
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
        applyIslLinkProps(sourceEndpoint, destEndpoint, islBuilder);
        Isl link = islBuilder.build();

        if (link.isUnderMaintenance()) {
            link.setCost(link.getCost() + islCostWhenUnderMaintenance);
        }

        log.debug("Create new DB object (prefilled): {}", link);
        return link;
    }

    private Anchor loadSwitch(Endpoint endpoint) {
        Switch sw = switchRepository.findById(endpoint.getDatapath())
                .orElseThrow(() -> new PersistenceException(
                        String.format("Switch %s not found in DB", endpoint.getDatapath())));
        return new Anchor(endpoint, sw);
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
            link.setMaxBandwidth(aggData.getAvailableBandwidth());
            link.setDefaultMaxBandwidth(aggData.getAvailableBandwidth());
        }
    }

    private void applyIslStatus(Isl link, DiscoveryEndpointStatus uniStatus, Instant timeNow) {
        IslStatus become = mapStatus(uniStatus);
        IslStatus aggStatus = mapStatus(getAggregatedStatus());
        if (link.getActualStatus() != become || link.getStatus() != aggStatus) {
            link.setTimeModify(timeNow);

            link.setActualStatus(become);
            link.setStatus(aggStatus);
        }
    }

    private void applyIslAvailableBandwidth(Isl link, Endpoint source, Endpoint dest) {
        IslDataHolder islData = discoveryFacts.makeAggregatedData();
        long availableBandwidth = 0;
        if (islData != null) {
            long usedBandwidth = flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                    source.getDatapath(), source.getPortNumber(),
                    dest.getDatapath(), dest.getPortNumber());
            availableBandwidth = islData.getAvailableBandwidth() - usedBandwidth;
        } else {
            log.error("There is no ISL data available for {}, unable to set available_bandwidth",
                      discoveryFacts.getReference());
        }
        link.setAvailableBandwidth(availableBandwidth);
    }

    private void applyIslCostRaiseOnPhysicalDown(Isl link, Instant timeNow) {
        if (link.getCost() < costRaiseOnPhysicalDown) {
            link.setTimeModify(timeNow);
            link.setCost(link.getCost() + costRaiseOnPhysicalDown);
        }
    }

    private void applyIslLinkProps(Endpoint source, Endpoint dest, IslBuilder isl) {
        Collection<LinkProps> linkProps = linkPropsRepository.findByEndpoints(
                source.getDatapath(), source.getPortNumber(),
                dest.getDatapath(), dest.getPortNumber());
        for (LinkProps entry : linkProps) {
            Integer cost = entry.getCost();
            if (cost != null) {
                isl.cost(cost);
            }

            Long maxBandwidth = entry.getMaxBandwidth();
            if (maxBandwidth != null) {
                isl.maxBandwidth(maxBandwidth);
            }

            // We can/should put "break" here but it lead to warnings... Anyway only one match possible
            // by such(full) query so we can avoid "break" here.
        }
    }

    private void pushIslChanges(Isl link) {
        log.debug("Write ISL object: {}", link);
        islRepository.createOrUpdate(link);
    }

    private DiscoveryEndpointStatus getAggregatedStatus() {
        DiscoveryEndpointStatus forward = endpointStatus.getForward();
        DiscoveryEndpointStatus reverse = endpointStatus.getReverse();
        if (forward == reverse) {
            return forward;
        }

        if (forward == DiscoveryEndpointStatus.MOVED || reverse == DiscoveryEndpointStatus.MOVED) {
            return DiscoveryEndpointStatus.MOVED;
        }

        return DiscoveryEndpointStatus.DOWN;
    }

    private boolean shouldUseBfd() {
        // TODO(surabujin): ensure the switch is BFD cappable

        if (!isGlobalBfdToggleEnabled()) {
            return false;
        }

        IslReference reference = discoveryFacts.getReference();
        return isPerIslBfdToggleEnabled(reference.getSource(), reference.getDest())
                && isPerIslBfdToggleEnabled(reference.getDest(), reference.getSource());
    }

    private boolean isGlobalBfdToggleEnabled() {
        return featureTogglesRepository.find()
                .map(FeatureToggles::getUseBfdForIslIntegrityCheck)
                .orElse(FeatureToggles.DEFAULTS.getUseBfdForIslIntegrityCheck());
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

    private void logDbException(Exception e) {
        log.error(
                String.format("Error in DB transaction for ISL %s: %s", discoveryFacts.getReference(), e.getMessage()),
                e);
    }

    private static IslStatus mapStatus(DiscoveryEndpointStatus status) {
        switch (status) {
            case UP:
                return IslStatus.ACTIVE;
            case DOWN:
                return IslStatus.INACTIVE;
            case MOVED:
                return IslStatus.MOVED;
            default:
                throw new IllegalArgumentException(
                        makeInvalidMappingMessage(DiscoveryEndpointStatus.class, IslStatus.class, status));
        }
    }

    private static DiscoveryEndpointStatus mapStatus(IslStatus status) {
        switch (status) {
            case ACTIVE:
                return DiscoveryEndpointStatus.UP;
            case INACTIVE:
                return DiscoveryEndpointStatus.DOWN;
            case MOVED:
                return DiscoveryEndpointStatus.MOVED;
            default:
                throw new IllegalArgumentException(
                        makeInvalidMappingMessage(IslStatus.class, DiscoveryEndpointStatus.class, status));
        }
    }

    private static String makeInvalidMappingMessage(Class<?> from, Class<?> to, Object value) {
        return String.format("There is no mapping defined between %s and %s for %s", from.getName(),
                             to.getName(), value);
    }

    private enum DiscoveryEndpointStatus {
        UP, DOWN, MOVED
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

        private Boolean physicalLinkDown;

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
