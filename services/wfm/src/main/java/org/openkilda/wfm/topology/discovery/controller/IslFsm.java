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
import org.openkilda.messaging.command.reroute.RerouteFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Isl;
import org.openkilda.model.Isl.IslBuilder;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.facts.DiscoveryFacts;

import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.time.Instant;
import java.util.Collection;

public final class IslFsm extends AbstractStateMachine<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> {
    private final IslRepository islRepository;
    private final LinkPropsRepository linkPropsRepository;
    private final TransactionManager transactionManager;
    private final FlowSegmentRepository flowSegmentRepository;

    private DiscoveryEndpointStatus sourceStatus = DiscoveryEndpointStatus.DOWN;
    private DiscoveryEndpointStatus destStatus = DiscoveryEndpointStatus.DOWN;

    private final DiscoveryFacts discoveryFacts;

    private static final StateMachineBuilder<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                IslFsm.class, IslFsmState.class, IslFsmEvent.class, IslFsmContext.class,
                // extra parameters
                PersistenceManager.class, IslReference.class);

        // DOWN
        builder.transition()
                .from(IslFsmState.DOWN).to(IslFsmState.UP_ATTEMPT).on(IslFsmEvent.ISL_UP)
                .callMethod("handleSourceDestUpState");
        builder.transition()
                .from(IslFsmState.DOWN).to(IslFsmState.MOVED).on(IslFsmEvent.ISL_MOVE);
        builder.internalTransition().within(IslFsmState.DOWN).on(IslFsmEvent.ISL_DOWN)
                .callMethod("handleSourceDestUpState");
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
                .from(IslFsmState.UP).to(IslFsmState.DOWN).on(IslFsmEvent.ISL_DOWN)
                .callMethod("handleSourceDestUpState");
        builder.transition()
                .from(IslFsmState.UP).to(IslFsmState.MOVED).on(IslFsmEvent.ISL_MOVE);
        builder.onEntry(IslFsmState.UP)
                .callMethod("upEnter");

        // MOVED
        builder.transition()
                .from(IslFsmState.MOVED).to(IslFsmState.UP_ATTEMPT).on(IslFsmEvent.ISL_UP)
                .callMethod("handleSourceDestUpState");
        builder.internalTransition()
                .within(IslFsmState.MOVED).on(IslFsmEvent.ISL_DOWN)
                .callMethod("handleSourceDestUpState");
        builder.onEntry(IslFsmState.MOVED)
                .callMethod("movedEnter");
    }

    public static FsmExecutor<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> makeExecutor() {
        return new FsmExecutor<>(IslFsmEvent.NEXT);
    }

    public static IslFsm create(PersistenceManager persistenceManager, IslReference reference) {
        return builder.newStateMachine(IslFsmState.DOWN, persistenceManager, reference);
    }

    private IslFsm(PersistenceManager persistenceManager, IslReference reference) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islRepository = repositoryFactory.createIslRepository();
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();

        transactionManager = persistenceManager.getTransactionManager();

        discoveryFacts = new DiscoveryFacts(reference);
    }

    // -- FSM actions --
    private void handleSourceDestUpState(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        DiscoveryEndpointStatus status;
        StatusChangeReason reason = null;
        switch (event) {
            case ISL_UP:
                status = DiscoveryEndpointStatus.UP;
                break;
            case ISL_DOWN:
                status = DiscoveryEndpointStatus.DOWN;
                if (context.getPhysicalLinkDown()) {
                    reason = StatusChangeReason.ENDPOINT_PHYSICAL_DOWN;
                }
                break;
            default:
                throw new IllegalStateException(String.format("Unexpected event %s for %s.handleSourceDestUpState",
                                                              event, getClass().getName()));
        }
        updateStatus(context, status, reason);
    }

    private void downEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        updatePersisted();
    }

    private void handleUpAttempt(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        IslFsmEvent route;
        if (getAggregatedStatus() == DiscoveryEndpointStatus.UP) {
            route = IslFsmEvent._UP_ATTEMPT_SUCCESS;
        } else {
            route = IslFsmEvent._UP_ATTEMPT_FAIL;
        }
        fire(route, context);
    }

    private void upEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        updatePersisted();

        IslReference reference = discoveryFacts.getReference();
        context.getOutput().notifyBiIslUp(reference.getSource(), reference);
        context.getOutput().notifyBiIslUp(reference.getDest(), reference);
    }

    private void movedEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        updateStatus(context, DiscoveryEndpointStatus.MOVED);
        updatePersisted();
        // emit isl-move
    }

    // -- private/service methods --

    private void updateStatus(IslFsmContext context, DiscoveryEndpointStatus status) {
        updateStatus(context, status, null);
    }

    private void updateStatus(IslFsmContext context, DiscoveryEndpointStatus status, StatusChangeReason reason) {
        DiscoveryEndpointStatus before = getAggregatedStatus();
        Endpoint endpoint = context.getEndpoint();
        updateSourceDestUpStatus(endpoint, status);
        DiscoveryEndpointStatus after = getAggregatedStatus();

        if (before != after) {
            String reportReason = compileStatusChangeReason(endpoint, after, reason);
            triggerReroute(context, after, reportReason);
        }
    }

    private void updateSourceDestUpStatus(Endpoint endpoint, DiscoveryEndpointStatus status) {
        IslReference reference = discoveryFacts.getReference();
        if (reference.getSource().equals(endpoint)) {
            sourceStatus = status;
        } else if (reference.getDest().equals(endpoint)) {
            destStatus = status;
        } else {
            throw new IllegalArgumentException(String.format("Endpoint %s is not part of ISL %s", endpoint, reference));
        }
    }

    private void triggerReroute(IslFsmContext context, DiscoveryEndpointStatus become, String reason) {
        RerouteFlows trigger;
        switch (become) {
            case UP:
                trigger = new RerouteInactiveFlows(reason);
                break;
            case DOWN:
            case MOVED:
                Endpoint source = discoveryFacts.getReference().getSource();
                PathNode pathNode = new PathNode(source.getDatapath(), source.getPortNumber(), 0);
                // FIXME (surabujin): why do we send only one ISL endpoint here?
                trigger = new RerouteAffectedFlows(pathNode, reason);
                break;
            default:
                throw new IllegalArgumentException(String.format(
                        "Unsupported value %s of %s", become, DiscoveryEndpointStatus.class.getName()));
        }

        context.getOutput().triggerReroute(trigger);
    }

    private void updatePersisted() {
        transactionManager.doInTransaction(() -> {
            Instant timeNow = Instant.now();
            IslReference islReference = discoveryFacts.getReference();

            updatePersisted(islReference.getSource(), islReference.getDest(), timeNow, destStatus);
            updatePersisted(islReference.getDest(), islReference.getSource(), timeNow, sourceStatus);
        });
    }

    private void updatePersisted(Endpoint source, Endpoint dest, Instant timeNow, DiscoveryEndpointStatus status) {
        Isl link = loadOrCreatePersistedIsl(source, dest, timeNow);
        updateCommonIslFields(link, timeNow);

        updateAvailableBandwidth(link, source, dest);

        link.setActualStatus(mapStatus(status));
        link.setStatus(mapStatus(getAggregatedStatus()));

        islRepository.createOrUpdate(link);
    }

    private Isl loadOrCreatePersistedIsl(Endpoint source, Endpoint dest, Instant timeNow) {
        return islRepository.findByEndpoints(
                source.getDatapath(), source.getPortNumber(), dest.getDatapath(), dest.getPortNumber())
                .orElseGet(() -> createPersistentIsl(source, dest, timeNow));
    }

    private Isl createPersistentIsl(Endpoint source, Endpoint dest, Instant timeNow) {
        IslBuilder islBuilder = Isl.builder()
                .timeModify(timeNow)
                .srcSwitch(makeSwitchRecord(source))
                .srcPort(source.getPortNumber())
                .destSwitch(makeSwitchRecord(dest))
                .destPort(dest.getPortNumber());
        applyLinkProps(source, dest, islBuilder);
        return islBuilder.build();
    }

    private Switch makeSwitchRecord(Endpoint endpoint) {
        return Switch.builder()
                .switchId(endpoint.getDatapath())
                .build();
    }

    private void updateCommonIslFields(Isl link, Instant timeNow) {
        link.setTimeModify(timeNow);
        link.setLatency(discoveryFacts.getLatency().intValue());
        link.setSpeed(discoveryFacts.getSpeed());
        link.setMaxBandwidth(discoveryFacts.getAvailableBandwidth());
        link.setDefaultMaxBandwidth(discoveryFacts.getAvailableBandwidth());
    }

    private void updateAvailableBandwidth(Isl link, Endpoint source, Endpoint dest) {
        long usedBandwidth = flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                source.getDatapath(), source.getPortNumber(),
                dest.getDatapath(), dest.getPortNumber());
        link.setAvailableBandwidth(discoveryFacts.getAvailableBandwidth() - usedBandwidth);
    }

    private void applyLinkProps(Endpoint source, Endpoint dest, IslBuilder isl) {
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

    private DiscoveryEndpointStatus getAggregatedStatus() {
        if (sourceStatus == destStatus) {
            return sourceStatus;
        }

        if (sourceStatus == DiscoveryEndpointStatus.MOVED || destStatus == DiscoveryEndpointStatus.MOVED) {
            return DiscoveryEndpointStatus.MOVED;
        }

        return DiscoveryEndpointStatus.DOWN;
    }

    private IslStatus mapStatus(DiscoveryEndpointStatus status) {
        switch (status) {
            case UP:
                return IslStatus.ACTIVE;
            case DOWN:
                return IslStatus.INACTIVE;
            case MOVED:
                return IslStatus.MOVED;
            default:
                throw new IllegalArgumentException(String.format(
                        "There is no mapping defined between %s and %s for %s", DiscoveryEndpointStatus.class.getName(),
                        IslStatus.class.getName(), status.toString()));
        }
    }

    private String compileStatusChangeReason(Endpoint endpoint, DiscoveryEndpointStatus become,
                                                    StatusChangeReason declaredReason) {
        if (declaredReason != null) {
            return compileStatusChangeReason(endpoint, declaredReason);
        }
        return compileStatusChangeReason(endpoint, become);
    }

    private String compileStatusChangeReason(Endpoint endpoint, StatusChangeReason declaredReason) {
        String reason;
        IslReference reference = discoveryFacts.getReference();

        switch (declaredReason) {
            case ENDPOINT_PHYSICAL_DOWN:

                reason = String.format("ISL %s become FAILED due to physical link DOWN event from %s",
                                       reference, endpoint);
                break;
            default:
                reason = String.format("ISL %s status change due to %s", reference, declaredReason);
        }
        return reason;
    }

    private String compileStatusChangeReason(Endpoint endpoint, DiscoveryEndpointStatus become) {
        return String.format("ISL %s become %s (changed endpoint %s)", discoveryFacts.getReference(), become, endpoint);
    }

    private enum DiscoveryEndpointStatus {
        UP, DOWN, MOVED
    }

    private enum StatusChangeReason {
        ENDPOINT_PHYSICAL_DOWN
    }
}
