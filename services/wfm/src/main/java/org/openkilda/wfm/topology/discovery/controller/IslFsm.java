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
import org.openkilda.wfm.share.utils.FsmExecutor;
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
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Slf4j
public final class IslFsm extends AbstractStateMachine<IslFsm, IslFsm.IslFsmState, IslFsm.IslFsmEvent,
        IslFsm.IslFsmContext> {
    private final IslRepository islRepository;
    private final LinkPropsRepository linkPropsRepository;
    private final FlowSegmentRepository flowSegmentRepository;
    private final SwitchRepository switchRepository;
    private final TransactionManager transactionManager;
    private final FeatureTogglesRepository featureTogglesRepository;

    private final int costRaiseOnPhysicalDown;

    private final BiIslDataHolder<DiscoveryEndpointStatus> endpointStatus;

    private final DiscoveryFacts discoveryFacts;

    private static final StateMachineBuilder<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                IslFsm.class, IslFsmState.class, IslFsmEvent.class, IslFsmContext.class,
                // extra parameters
                PersistenceManager.class, DiscoveryOptions.class, IslReference.class);

        String updateEndpointStatusMethod = "updateEndpointStatus";

        // DOWN
        builder.transition()
                .from(IslFsmState.DOWN).to(IslFsmState.UP_ATTEMPT).on(IslFsmEvent.ISL_UP)
                .callMethod(updateEndpointStatusMethod);
        builder.transition()
                .from(IslFsmState.DOWN).to(IslFsmState.MOVED).on(IslFsmEvent.ISL_MOVE)
                .callMethod(updateEndpointStatusMethod);
        builder.internalTransition().within(IslFsmState.DOWN).on(IslFsmEvent.ISL_DOWN)
                .callMethod(updateEndpointStatusMethod);
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
                .callMethod(updateEndpointStatusMethod);
        builder.onEntry(IslFsmState.MOVED)
                .callMethod("movedEnter");
    }

    public static FsmExecutor<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> makeExecutor() {
        return new FsmExecutor<>(IslFsmEvent.NEXT);
    }

    /**
     * Use "history" data to determine initial FSM state and to pre-fill internal ISL representation.
     */
    public static IslFsm createFromHistory(PersistenceManager persistenceManager, DiscoveryOptions options,
                                           IslReference reference, Isl history) {
        IslFsmState initialState;
        switch (history.getStatus()) {
            case ACTIVE:
                initialState = IslFsmState.UP;
                break;
            case INACTIVE:
                initialState = IslFsmState.DOWN;
                break;
            case MOVED:
                initialState = IslFsmState.MOVED;
                break;
            default:
                throw new IllegalArgumentException(makeInvalidMappingMessage(
                        history.getStatus().getClass(), IslFsmState.class, history.getStatus()));
        }

        IslFsm fsm = builder.newStateMachine(initialState, persistenceManager, options, reference);
        fsm.applyHistory(history);
        return fsm;
    }

    public static IslFsm create(PersistenceManager persistenceManager, DiscoveryOptions options,
                                IslReference reference) {
        return builder.newStateMachine(IslFsmState.DOWN, persistenceManager, options, reference);
    }

    private IslFsm(PersistenceManager persistenceManager, DiscoveryOptions options, IslReference reference) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islRepository = repositoryFactory.createIslRepository();
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();

        transactionManager = persistenceManager.getTransactionManager();

        costRaiseOnPhysicalDown = options.getIslCostRaiseOnPhysicalDown();

        endpointStatus = new BiIslDataHolder<>(reference);
        endpointStatus.putBoth(DiscoveryEndpointStatus.DOWN);

        discoveryFacts = new DiscoveryFacts(reference);
    }

    // -- FSM actions --
    private void updateEndpointStatus(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        updateEndpointStatusByEvent(event, context);
    }

    private void downEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);
        saveStatusTransaction();
    }

    private void handleUpAttempt(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        discoveryFacts.put(context.getEndpoint(), context.getIslData());

        IslFsmEvent route;
        if (getAggregatedStatus() == DiscoveryEndpointStatus.UP) {
            route = IslFsmEvent._UP_ATTEMPT_SUCCESS;
        } else {
            route = IslFsmEvent._UP_ATTEMPT_FAIL;
        }
        fire(route, context);
    }

    private void upEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);

        saveAllTransaction();
        triggerDownFlowReroute(context);

        if (shouldUseBfd()) {
            setupBfd(context);
        }
    }

    private void upExit(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} is no more UP (physical-down:{})",
                  discoveryFacts.getReference(), to, context.getPhysicalLinkDown());

        updateEndpointStatusByEvent(event, context);
        saveStatusAndCostRaiseTransaction(context);
        triggerAffectedFlowReroute(context);
    }

    private void movedEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} become {}", discoveryFacts.getReference(), to);
        saveStatusTransaction();
    }

    private void handleBfdEnableDisable(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        if (context.getBfdEnable()) {
            setupBfd(context);
        } else {
            killBfd(context);
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
        if (context.getPhysicalLinkDown()) {
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

    private boolean shouldUseBfd() {
        // TODO(surabujin): ensure BFD enabled
        return true;
    }

    private void setupBfd(IslFsmContext context) {
        IslReference reference = discoveryFacts.getReference();
        context.getOutput().bfdEnableRequest(reference.getSource(), reference);
        context.getOutput().bfdEnableRequest(reference.getDest(), reference);
    }

    private void killBfd(IslFsmContext context) {
        // TODO
    }

    private void saveAllTransaction() {
        transactionManager.doInTransaction(() -> saveAll(Instant.now()));
    }

    private void saveStatusTransaction() {
        transactionManager.doInTransaction(() -> saveStatus(Instant.now()));
    }

    private void saveStatusAndCostRaiseTransaction(IslFsmContext context) {
        transactionManager.doInTransaction(() -> {
            Instant timeNow = Instant.now();

            saveStatus(timeNow);
            if (context.getPhysicalLinkDown()) {
                raiseCostOnPhysicalDown(timeNow);
            }
        });
    }

    private void saveAll(Instant timeNow) {
        IslReference reference = discoveryFacts.getReference();
        saveAll(reference.getSource(), reference.getDest(), timeNow, endpointStatus.getForward());
        saveAll(reference.getDest(), reference.getSource(), timeNow, endpointStatus.getReverse());
    }

    private void saveAll(Endpoint source, Endpoint dest, Instant timeNow, DiscoveryEndpointStatus uniStatus) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        link.setTimeModify(timeNow);

        applyIslGenericData(link);
        applyIslAvailableBandwidth(link, source, dest);
        applyIslStatus(link, uniStatus, timeNow);

        pushIslChanges(link);
    }

    private void saveStatus(Instant timeNow) {
        IslReference reference = discoveryFacts.getReference();
        saveStatus(reference.getSource(), reference.getDest(), timeNow, endpointStatus.getForward());
        saveStatus(reference.getDest(), reference.getSource(), timeNow, endpointStatus.getReverse());
    }

    private void saveStatus(Endpoint source, Endpoint dest, Instant timeNow, DiscoveryEndpointStatus uniStatus) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        applyIslStatus(link, uniStatus, timeNow);
        pushIslChanges(link);
    }

    private void raiseCostOnPhysicalDown(Instant timeNow) {
        IslReference reference = discoveryFacts.getReference();

        raiseCostOnPhysicalDown(timeNow, reference.getSource(), reference.getDest());
        raiseCostOnPhysicalDown(timeNow, reference.getDest(), reference.getSource());
    }

    private void raiseCostOnPhysicalDown(Instant timeNow, Endpoint source, Endpoint dest) {
        Isl link = loadOrCreateIsl(source, dest, timeNow);

        log.debug("Raise ISL {} ===> {} cost due to physical down (cost-now:{}, raise:{})",
                  source, dest, link.getCost(), costRaiseOnPhysicalDown);
        applyIslCostRaiseOnPhysicalDown(link, timeNow);
        pushIslChanges(link);
    }

    private Isl loadOrCreateIsl(Endpoint source, Endpoint dest, Instant timeNow) {
        return islRepository.findByEndpoints(
                source.getDatapath(), source.getPortNumber(), dest.getDatapath(), dest.getPortNumber())
                .map(link -> {
                    log.debug("Read ISL object: {}", link);
                    switchRepository.lockSwitches(link.getSrcSwitch(), link.getDestSwitch());
                    return link;
                })
                .orElseGet(() -> createIsl(source, dest, timeNow));
    }

    private Isl createIsl(Endpoint source, Endpoint dest, Instant timeNow) {
        Switch sourceSwitch = loadSwitch(source);
        Switch destSwitch = loadSwitch(dest);
        switchRepository.lockSwitches(sourceSwitch, destSwitch);

        IslBuilder islBuilder = Isl.builder()
                .timeCreate(timeNow)
                .timeModify(timeNow)
                .srcSwitch(sourceSwitch)
                .srcPort(source.getPortNumber())
                .destSwitch(destSwitch)
                .destPort(dest.getPortNumber());
        applyIslLinkProps(source, dest, islBuilder);
        Isl link = islBuilder.build();
        log.debug("Create new DB object (prefilled): {}", link);
        return link;
    }

    private Switch loadSwitch(Endpoint endpoint) {
        return switchRepository.findById(endpoint.getDatapath())
                .orElseThrow(() -> new PersistenceException(
                        String.format("Switch %s not found in DB", endpoint.getDatapath())));
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
        IslDataHolder islData = discoveryFacts.get(source);
        long availableBandwidth = 0;
        if (islData != null) {
            long usedBandwidth = flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                    source.getDatapath(), source.getPortNumber(),
                    dest.getDatapath(), dest.getPortNumber());
            availableBandwidth = islData.getAvailableBandwidth() - usedBandwidth;
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

    // TODO(surabujin): move this check into reroute topology
    private boolean shouldEmitDownFlowReroute() {
        Optional<FeatureToggles> featureToggles = featureTogglesRepository.find();
        return featureToggles.isPresent()
                && featureToggles.get().getFlowsRerouteOnIslDiscoveryEnabled() != null
                && featureToggles.get().getFlowsRerouteOnIslDiscoveryEnabled();
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
    @Builder
    public static class IslFsmContext {
        private final IIslCarrier output;

        private final Endpoint endpoint;

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

        ISL_UP, ISL_DOWN, ISL_MOVE,
        _UP_ATTEMPT_SUCCESS, _UP_ATTEMPT_FAIL
    }

    public enum IslFsmState {
        UP, DOWN,
        MOVED,

        UP_ATTEMPT
    }
}
