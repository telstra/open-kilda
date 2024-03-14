/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.network.controller.isl;

import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.Isl;
import org.openkilda.model.Isl.IslBuilder;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.mappers.IslMapper;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.controller.isl.IslFsm.IslFsmContext;
import org.openkilda.wfm.topology.network.controller.isl.IslFsm.IslFsmEvent;
import org.openkilda.wfm.topology.network.controller.isl.IslFsm.IslFsmState;
import org.openkilda.wfm.topology.network.model.BiIslDataHolder;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;
import org.openkilda.wfm.topology.network.service.IIslCarrier;

import com.google.common.collect.ImmutableList;
import dev.failsafe.RetryPolicy;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.Condition;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public final class IslFsm extends AbstractBaseFsm<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> {
    private final Clock clock;
    private final NetworkOptions options;

    private final IslReference reference;

    private long islRulesAttempts;

    private DiscoveryBfdMonitor discoveryBfdMonitor;
    private List<DiscoveryMonitor<?>> monitorsByPriority = Collections.emptyList();
    private StatusAggregator statusAggregator = new StatusAggregator();

    private final BiIslDataHolder<Boolean> endpointResourcesManagementCompleteStatus;

    private final NetworkTopologyDashboardLogger dashboardLogger;

    private final IslRepository islRepository;
    private final LinkPropsRepository linkPropsRepository;
    private final FlowPathRepository flowPathRepository;
    private final SwitchRepository switchRepository;
    private final TransactionManager transactionManager;
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;

    private final RetryPolicy<?> transactionRetryPolicy;

    public static IslFsmFactory factory(Clock clock, PersistenceManager persistenceManager,
                                        NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder) {
        return new IslFsmFactory(clock, persistenceManager, dashboardLoggerBuilder);
    }

    public IslFsm(Clock clock, PersistenceManager persistenceManager, NetworkTopologyDashboardLogger dashboardLogger,
                  NetworkOptions options, IslReference reference) {
        this.clock = clock;
        this.options = options;

        this.reference = reference;

        this.dashboardLogger = dashboardLogger;

        endpointResourcesManagementCompleteStatus = new BiIslDataHolder<>(reference);

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islRepository = repositoryFactory.createIslRepository();
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();

        transactionManager = persistenceManager.getTransactionManager();
        transactionRetryPolicy = transactionManager.getDefaultRetryPolicy()
                .withMaxDuration(Duration.ofSeconds(options.getDbRepeatMaxDurationSeconds()))
                .build();
    }

    // -- FSM actions --

    public void operationalEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        discoveryBfdMonitor = new DiscoveryBfdMonitor(reference);
        monitorsByPriority = ImmutableList.of(
                new DiscoveryMovedMonitor(reference),
                new DiscoveryPortStatusMonitor(reference),
                discoveryBfdMonitor,
                new DiscoveryRoundTripMonitor(reference),
                new DiscoveryPollMonitor(reference));

        transactionManager.doInTransaction(() -> {
            loadPersistentData(reference.getSource(), reference.getDest());
            loadPersistentData(reference.getDest(), reference.getSource());
        });

        statusAggregator = evaluateStatus();
        if (statusAggregator.getEffectiveStatus() == IslStatus.ACTIVE) {
            sendBfdPropertiesUpdate(context.getOutput());
        }
    }

    public void operationalExit(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        sendBfdDisable(context.getOutput());
        disableAuxiliaryPollMode(context.getOutput());
    }

    public void updateMonitorsAction(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        final boolean isBfdOperationalNow = discoveryBfdMonitor.isOperational();
        boolean isSyncRequired = false;
        for (DiscoveryMonitor<?> entry : monitorsByPriority) {
            isSyncRequired |= entry.update(event, context);
        }

        if (swapStatusAggregator()) {
            fireBecomeStateEvent(context);
        } else if (isSyncRequired) {
            fire(IslFsmEvent._FLUSH);
        }

        if (!isBfdOperationalNow && discoveryBfdMonitor.isOperational()) {
            enableAuxiliaryPollMode(context.getOutput());
        } else if (isBfdOperationalNow && !discoveryBfdMonitor.isOperational()) {
            disableAuxiliaryPollMode(context.getOutput());
        }
    }

    public void flushAction(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        flushTransaction();
    }

    public void removeAttempt(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        if (statusAggregator.getEffectiveStatus() != IslStatus.ACTIVE) {
            fire(IslFsmEvent._REMOVE_CONFIRMED, context);
        }
    }

    public void bfdPropertiesUpdateAction(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        fire(IslFsmEvent.BFD_KILL, context);  // to avoid race condition during bfd session update
        sendBfdPropertiesUpdate(context.getOutput(), false);
    }

    public void setUpResourcesEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} initiate speaker resources setup process", reference);

        islRulesAttempts = options.getRulesSynchronizationAttempts();
        endpointResourcesManagementCompleteStatus.putBoth(false);

        sendInstallResources(context.getOutput());

        if (isResourcesManagementCompleted()) {
            fire(IslFsmEvent._RESOURCES_DONE, context);
        }
    }

    // FIXME(surabujin): protect from stale responses
    public void handleInstalledRule(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        Endpoint endpoint = context.getEndpoint();
        if (endpoint == null) {
            throw new IllegalArgumentException(makeInvalidResourceManipulationResponseMessage());
        }

        log.info("Receive response on ISL resource allocation request for {} (from {})",
                reference, endpoint.getDatapath());
        endpointResourcesManagementCompleteStatus.put(endpoint, true);
        if (isResourcesManagementCompleted()) {
            fire(IslFsmEvent._RESOURCES_DONE, context);
        }
    }

    public void setUpResourcesFailed(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        if (--islRulesAttempts >= 0) {
            long maxAttempts = options.getRulesSynchronizationAttempts();
            log.info("Retrying ISL resources setup for {} (attempt {} of {})",
                    reference, maxAttempts - islRulesAttempts, maxAttempts);
            sendInstallResources(context.getOutput());
        } else {
            log.warn("Failed to install rules for multi table mode on isl {}, required manual rule sync",
                    reference);
            fire(IslFsmEvent._RESOURCES_DONE, context);
        }
    }

    public void usableEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        dashboardLogger.onIslUp(reference, statusAggregator.getDetails());

        flushTransaction();
        sendBfdPropertiesUpdate(context.getOutput());

        triggerDownFlowReroute(context);
        sendIslChangedNotification(context.getOutput());
    }

    public void inactiveEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        dashboardLogger.onIslDown(reference, statusAggregator.getDetails());
        flushTransaction();

        sendIslStatusUpdateNotification(context, IslStatus.INACTIVE);
        triggerAffectedFlowReroute(context);
    }

    public void movedEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        dashboardLogger.onIslMoved(reference, statusAggregator.getDetails());
        flushTransaction();

        sendBfdDisable(context.getOutput());
        disableAuxiliaryPollMode(context.getOutput());
        sendIslStatusUpdateNotification(context, IslStatus.MOVED);
        triggerAffectedFlowReroute(context);
        sendIslChangedNotification(context.getOutput());
    }

    public void cleanUpResourcesEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("ISL {} initiate speaker resources removal process", reference);

        islRulesAttempts = options.getRulesSynchronizationAttempts();
        endpointResourcesManagementCompleteStatus.putBoth(false);

        sendRemoveIslResources(context.getOutput());

        if (isResourcesManagementCompleted()) {
            fire(IslFsmEvent._RESOURCES_DONE, context);
        }
    }

    // FIXME(surabujin): protect from stale responses
    public void handleRemovedRule(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        Endpoint endpoint = context.getEndpoint();
        if (endpoint == null) {
            throw new IllegalArgumentException(makeInvalidResourceManipulationResponseMessage());
        }

        log.info("Receive response on ISL resource release request for {} (from {})",
                reference, endpoint.getDatapath());
        endpointResourcesManagementCompleteStatus.put(endpoint, true);
        if (isResourcesManagementCompleted()) {
            fire(IslFsmEvent._RESOURCES_DONE, context);
        }
    }

    public void cleanUpResourcesFailed(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        if (--islRulesAttempts >= 0) {
            long maxAttempts = options.getRulesSynchronizationAttempts();
            log.info("Retrying ISL resources removal for {} (attempt {} of {})",
                    reference, maxAttempts - islRulesAttempts, maxAttempts);
            sendRemoveIslResources(context.getOutput());
        } else {
            log.warn("Failed to remove rules for multi table mode on isl {}, required manual rule sync",
                    reference);
            fire(IslFsmEvent._RESOURCES_DONE, context);
        }
    }

    public void deletedEnter(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("Isl FSM for {} have reached termination state (ready for being removed)", reference);
        sendIslRemovedNotification(context.getOutput());
    }

    public void resurrectNotification(IslFsmState from, IslFsmState to, IslFsmEvent event, IslFsmContext context) {
        log.info("Resurect ISL {} due to event {} on {}", reference, event, context.getEndpoint());
    }

    // -- private/service methods --
    private void loadPersistentData(Endpoint start, Endpoint end) {
        Optional<Isl> potentialIsl = islRepository.findByEndpoints(
                start.getDatapath(), start.getPortNumber(),
                end.getDatapath(), end.getPortNumber());
        if (potentialIsl.isPresent()) {
            Isl isl = potentialIsl.get();

            for (DiscoveryMonitor<?> entry : monitorsByPriority) {
                entry.load(start, isl);
            }

            fixUpPersistentData(isl);
        } else {
            log.debug("There is no persistent ISL data {} ==> {} (do not load monitors state)", start, end);
        }
    }

    private void fixUpPersistentData(Isl link) {
        link.setBfdSessionStatus(null);
    }

    private void sendInstallResources(IIslCarrier carrier) {
        final Endpoint source = reference.getSource();
        final Endpoint dest = reference.getDest();
        sendInstallResources(carrier, source);
        sendInstallResources(carrier, dest);
    }

    private void sendInstallResources(IIslCarrier carrier, Endpoint ingress) {
        Boolean isCompleted = endpointResourcesManagementCompleteStatus.get(ingress);
        if (isCompleted != null && isCompleted) {
            return;
        }

        log.info("Emit ISL resource allocation request for {} to {}", reference, ingress.getDatapath());
        carrier.islRulesInstall(reference, ingress);
    }

    private void sendIslRemovedNotification(IIslCarrier carrier) {
        carrier.islRemovedNotification(reference.getSource(), reference);
        carrier.islRemovedNotification(reference.getDest(), reference);
        carrier.islChangedNotifyFlowMonitor(reference, true);
    }

    private void sendRemoveIslResources(IIslCarrier carrier) {
        final Endpoint source = reference.getSource();
        final Endpoint dest = reference.getDest();
        sendRemoveIslResources(carrier, source, dest);
        sendRemoveIslResources(carrier, dest, source);
    }

    private void sendRemoveIslResources(IIslCarrier carrier, Endpoint ingress, Endpoint egress) {
        Boolean isCompleted = endpointResourcesManagementCompleteStatus.get(ingress);
        if (isCompleted != null && isCompleted) {
            return;
        }

        // TODO(surabujin): why for we need this check?
        boolean isIslRemoved = islRepository.findByEndpoint(ingress.getDatapath(), ingress.getPortNumber())
                .isEmpty();
        if (isIslRemoved) {
            log.info("Emit ISL resource release request for {} to {}", reference, ingress.getDatapath());
            carrier.islRulesDelete(reference, ingress);
        } else {
            endpointResourcesManagementCompleteStatus.put(ingress, true);
        }
    }

    private void sendBfdPropertiesUpdate(IIslCarrier carrier) {
        sendBfdPropertiesUpdate(carrier, true);
    }

    private void sendBfdPropertiesUpdate(IIslCarrier carrier, boolean onlyIfEnabled) {
        if (canSetupBfd()) {
            BfdProperties properties = loadBfdProperties();
            if (! onlyIfEnabled || properties.isEnabled()) {
                sendBfdPropertiesUpdate(carrier, properties);
            }
        }
    }

    private void sendBfdPropertiesUpdate(IIslCarrier carrier, BfdProperties properties) {
        carrier.bfdPropertiesApplyRequest(reference.getSource(), reference, properties);
        carrier.bfdPropertiesApplyRequest(reference.getDest(), reference, properties);
    }

    private void sendBfdDisable(IIslCarrier carrier) {
        carrier.bfdDisableRequest(reference.getSource());
        carrier.bfdDisableRequest(reference.getDest());
    }

    private void sendIslStatusUpdateNotification(IslFsmContext context, IslStatus status) {
        IslStatusUpdateNotification trigger = new IslStatusUpdateNotification(
                reference.getSource().getDatapath(), reference.getSource().getPortNumber(),
                reference.getDest().getDatapath(), reference.getDest().getPortNumber(),
                status);
        context.getOutput().islStatusUpdateNotification(trigger);
    }

    private void sendIslChangedNotification(IIslCarrier carrier) {
        carrier.islChangedNotifyFlowMonitor(reference, false);
    }

    /**
     * Send enable auxiliary poll mode request in case when BFD becomes active.
     */
    public void enableAuxiliaryPollMode(IIslCarrier carrier) {
        carrier.auxiliaryPollModeUpdateRequest(reference.getSource(), true);
        carrier.auxiliaryPollModeUpdateRequest(reference.getDest(), true);
    }

    /**
     * Send disable auxiliary poll mode request in case when BFD becomes inactive.
     */
    public void disableAuxiliaryPollMode(IIslCarrier carrier) {
        carrier.auxiliaryPollModeUpdateRequest(reference.getSource(), false);
        carrier.auxiliaryPollModeUpdateRequest(reference.getDest(), false);
    }

    private void triggerAffectedFlowReroute(IslFsmContext context) {
        String reason = makeRerouteAffectedReason(context.getEndpoint());

        // FIXME (surabujin): why do we send only one ISL endpoint here?
        Endpoint ingress = reference.getSource();
        PathNode pathNode = new PathNode(ingress.getDatapath(), ingress.getPortNumber(), 0);
        RerouteAffectedFlows trigger = new RerouteAffectedFlows(pathNode, reason);
        context.getOutput().triggerReroute(trigger);
    }

    private void triggerDownFlowReroute(IslFsmContext context) {
        if (shouldEmitDownFlowReroute()) {
            Endpoint ingress = reference.getSource();
            PathNode pathNode = new PathNode(ingress.getDatapath(), ingress.getPortNumber(), 0);
            RerouteInactiveFlows trigger = new RerouteInactiveFlows(pathNode, String.format(
                    "ISL %s status become %s", reference, IslStatus.ACTIVE));
            context.getOutput().triggerReroute(trigger);
        }
    }

    private void flushTransaction() {
        transactionManager.doInTransaction(transactionRetryPolicy, () -> flush(clock.instant()));
    }

    private void flush(Instant timeNow) {
        Socket socket = prepareSocket();
        flush(socket.getSource(), socket.getDest(), timeNow);
        flush(socket.getDest(), socket.getSource(), timeNow);
    }

    private void flush(Anchor source, Anchor dest, Instant timeNow) {
        Optional<Isl> storedIsl = loadIsl(source.getEndpoint(), dest.getEndpoint());
        Isl link = storedIsl.orElseGet(() -> createIsl(source, dest, timeNow));

        link.setTimeModify(timeNow);

        long maxBandwidth = link.getMaxBandwidth();
        for (DiscoveryMonitor<?> entry : monitorsByPriority) {
            entry.flush(source.getEndpoint(), link);
        }

        applyIslMaxBandwidth(link, source.getEndpoint(), dest.getEndpoint());
        if (!storedIsl.isPresent() || maxBandwidth != link.getMaxBandwidth()) {
            applyIslAvailableBandwidth(link, source.getEndpoint(), dest.getEndpoint());
        }

        link.setStatus(statusAggregator.getEffectiveStatus());
        if (statusAggregator.getEffectiveStatus() == IslStatus.INACTIVE) {
            link.setDownReason(statusAggregator.getDownReason());
        } else {
            link.setDownReason(null);
        }

        log.debug("Write ISL object: {}", link);
    }

    private boolean swapStatusAggregator() {
        IslStatus current = statusAggregator.getEffectiveStatus();
        statusAggregator = evaluateStatus();
        log.debug("ISL {} status evaluation details - {}", reference, statusAggregator.getDetails());
        return current != statusAggregator.getEffectiveStatus();
    }

    private StatusAggregator evaluateStatus() {
        StatusAggregator aggregator = new StatusAggregator();
        monitorsByPriority.forEach(aggregator::evaluate);
        return aggregator;
    }

    private void fireBecomeStateEvent(IslFsmContext context) {
        IslFsmEvent route;
        final IslStatus effectiveStatus = statusAggregator.getEffectiveStatus();
        switch (effectiveStatus) {
            case ACTIVE:
                route = IslFsmEvent._BECOME_UP;
                break;
            case INACTIVE:
                route = IslFsmEvent._BECOME_DOWN;
                break;
            case MOVED:
                route = IslFsmEvent._BECOME_MOVED;
                break;
            default:
                throw new IllegalArgumentException(makeInvalidMappingMessage(
                        effectiveStatus.getClass(), IslFsmEvent.class, effectiveStatus));
        }
        fire(route, context);
    }

    private boolean canSetupBfd() {
        // TODO(surabujin): ensure the switch is BFD capable
        return true;
    }

    private Socket prepareSocket() {
        Anchor source = loadSwitchCreateIfMissing(reference.getSource());
        Anchor dest = loadSwitchCreateIfMissing(reference.getDest());

        return new Socket(source, dest);
    }

    private Isl createIsl(Anchor source, Anchor dest, Instant timeNow) {
        final Endpoint sourceEndpoint = source.getEndpoint();
        final Endpoint destEndpoint = dest.getEndpoint();
        IslBuilder islBuilder = Isl.builder()
                .srcSwitch(source.getSw())
                .srcPort(sourceEndpoint.getPortNumber())
                .destSwitch(dest.getSw())
                .destPort(destEndpoint.getPortNumber())
                .underMaintenance(isUnderMaintenance(source, dest));
        initializeFromLinkProps(sourceEndpoint, destEndpoint, islBuilder);
        Isl link = islBuilder.build();

        log.info("Create new DB object (prefilled): {}", link);
        islRepository.add(link);
        return link;
    }

    private boolean isUnderMaintenance(Anchor source, Anchor dest) {
        return featureTogglesRepository.getOrDefault().getDiscoverNewIslsInUnderMaintenanceMode()
                || source.getSw().isUnderMaintenance()
                || dest.getSw().isUnderMaintenance();
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
                .description(String.format("auto created as part of ISL %s discovery", reference))
                .build();
        switchRepository.add(sw);
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
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(
                source.getDatapath(), source.getPortNumber(),
                dest.getDatapath(), dest.getPortNumber());
        link.setAvailableBandwidth(link.getMaxBandwidth() - usedBandwidth);
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

    private BfdProperties loadBfdProperties() {
        BfdProperties leftToRight = loadBfdProperties(reference.getSource(), reference.getDest());
        BfdProperties rightToLeft = loadBfdProperties(reference.getDest(), reference.getSource());

        if (! leftToRight.equals(rightToLeft)) {
            log.error(
                    "ISL {} records contains not equal BFD properties data {} != {} (use {})",
                    reference, leftToRight, rightToLeft, leftToRight);
        }
        return leftToRight;
    }

    private BfdProperties loadBfdProperties(Endpoint source, Endpoint dest) {
        return loadIsl(source, dest)
                .map(IslMapper.INSTANCE::readBfdProperties)
                .orElseThrow(() -> new PersistenceException(
                        String.format("Isl %s ===> %s record not found in DB", source, dest)));
    }

    private boolean isResourcesManagementCompleted() {
        return endpointResourcesManagementCompleteStatus.stream()
                .filter(Objects::nonNull)
                .allMatch(entry -> entry);
    }

    // TODO(surabujin): should this check been moved into reroute topology?
    private boolean shouldEmitDownFlowReroute() {
        return featureTogglesRepository.getOrDefault().getFlowsRerouteOnIslDiscoveryEnabled();
    }

    private String makeRerouteAffectedReason(Endpoint endpoint) {
        final IslDownReason downReason = statusAggregator.getDownReason();
        final IslStatus effectiveStatus = statusAggregator.getEffectiveStatus();
        if (downReason == null) {
            return String.format("ISL %s status become %s", reference, effectiveStatus);
        }

        String humanReason;
        switch (downReason) {
            case PORT_DOWN:
                humanReason = String.format("ISL %s become %s due to physical link DOWN event on %s",
                                            reference, effectiveStatus, endpoint);
                break;
            case POLL_TIMEOUT:
                humanReason = String.format("ISL %s become %s because of FAIL TIMEOUT (endpoint:%s)",
                                            reference, effectiveStatus, endpoint);
                break;
            case BFD_DOWN:
                humanReason = String.format("ISL %s become %s because BFD detect link failure (endpoint:%s)",
                                            reference, effectiveStatus, endpoint);
                break;

            default:
                humanReason = String.format("ISL %s become %s (endpoint:%s, reason:%s)",
                                            reference, effectiveStatus, endpoint, downReason);
        }

        return humanReason;
    }

    private String makeInvalidResourceManipulationResponseMessage() {
        return String.format(
                "Receive corrupted response on ISL resources allocation request for %s - endpoint info is missing",
                reference);
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

    public static class IslFsmFactory {
        private final Clock clock;

        private final NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder;

        private final PersistenceManager persistenceManager;
        private final StateMachineBuilder<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> builder;

        IslFsmFactory(Clock clock, PersistenceManager persistenceManager,
                      NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder) {
            this.clock = clock;

            this.persistenceManager = persistenceManager;
            this.dashboardLoggerBuilder = dashboardLoggerBuilder;

            builder = StateMachineBuilderFactory.create(
                    IslFsm.class, IslFsmState.class, IslFsmEvent.class, IslFsmContext.class,
                    // extra parameters
                    Clock.class, PersistenceManager.class, NetworkTopologyDashboardLogger.class,
                    NetworkOptions.class, IslReference.class);

            // OPERATIONAL
            builder.defineSequentialStatesOn(
                    IslFsmState.OPERATIONAL,
                    IslFsmState.PENDING, IslFsmState.ACTIVE, IslFsmState.INACTIVE, IslFsmState.MOVED);

            builder.transition()
                    .from(IslFsmState.OPERATIONAL).to(IslFsmState.CLEAN_UP_RESOURCES)
                    .on(IslFsmEvent._REMOVE_CONFIRMED);
            builder.onEntry(IslFsmState.OPERATIONAL)
                    .callMethod("operationalEnter");
            builder.onExit(IslFsmState.OPERATIONAL)
                    .callMethod("operationalExit");

            // PENDING
            builder.transition()
                    .from(IslFsmState.PENDING).to(IslFsmState.ACTIVE).on(IslFsmEvent._BECOME_UP);
            builder.transition()
                    .from(IslFsmState.PENDING).to(IslFsmState.INACTIVE).on(IslFsmEvent._BECOME_DOWN);
            builder.transition()
                    .from(IslFsmState.PENDING).to(IslFsmState.MOVED).on(IslFsmEvent._BECOME_MOVED);
            setupOperationalInternalTransitions(builder, IslFsmState.PENDING);

            // ACTIVE
            builder.defineSequentialStatesOn(
                    IslFsmState.ACTIVE,
                    IslFsmState.SET_UP_RESOURCES, IslFsmState.USABLE);

            builder.transition()
                    .from(IslFsmState.ACTIVE).to(IslFsmState.INACTIVE).on(IslFsmEvent._BECOME_DOWN);
            builder.transition()
                    .from(IslFsmState.ACTIVE).to(IslFsmState.MOVED).on(IslFsmEvent._BECOME_MOVED);

            // SET_UP_RESOURCES
            builder.transition()
                    .from(IslFsmState.SET_UP_RESOURCES).to(IslFsmState.USABLE).on(IslFsmEvent._RESOURCES_DONE);
            builder.transition()
                    .from(IslFsmState.SET_UP_RESOURCES).to(IslFsmState.INACTIVE).on(IslFsmEvent._BECOME_DOWN);
            builder.transition()
                    .from(IslFsmState.SET_UP_RESOURCES).to(IslFsmState.MOVED).on(IslFsmEvent._BECOME_MOVED);
            builder.onEntry(IslFsmState.SET_UP_RESOURCES)
                    .callMethod("setUpResourcesEnter");
            builder.internalTransition()
                    .within(IslFsmState.SET_UP_RESOURCES).on(IslFsmEvent.ISL_RULE_INSTALLED)
                    .callMethod("handleInstalledRule");
            builder.internalTransition()
                    .within(IslFsmState.SET_UP_RESOURCES).on(IslFsmEvent.ISL_RULE_FAILED)
                    .callMethod("setUpResourcesFailed");
            setupOperationalInternalTransitions(builder, IslFsmState.SET_UP_RESOURCES);

            // USABLE
            builder.onEntry(IslFsmState.USABLE)
                    .callMethod("usableEnter");
            setupOperationalInternalTransitions(builder, IslFsmState.USABLE);

            // INACTIVE
            builder.transition()
                    .from(IslFsmState.INACTIVE).to(IslFsmState.ACTIVE).on(IslFsmEvent._BECOME_UP);
            builder.transition()
                    .from(IslFsmState.INACTIVE).to(IslFsmState.MOVED).on(IslFsmEvent._BECOME_MOVED);
            builder.onEntry(IslFsmState.INACTIVE)
                    .callMethod("inactiveEnter");
            setupOperationalInternalTransitions(builder, IslFsmState.INACTIVE);

            // MOVED
            builder.transition()
                    .from(IslFsmState.MOVED).to(IslFsmState.ACTIVE).on(IslFsmEvent._BECOME_UP);
            builder.onEntry(IslFsmState.MOVED)
                    .callMethod("movedEnter");
            setupOperationalInternalTransitions(builder, IslFsmState.MOVED);

            // CLEAN_UP_RESOURCES
            builder.transition()
                    .from(IslFsmState.CLEAN_UP_RESOURCES).to(IslFsmState.DELETED).on(IslFsmEvent._RESOURCES_DONE);
            for (IslFsmEvent event : new IslFsmEvent[]{IslFsmEvent.ISL_UP, IslFsmEvent.ISL_MOVE}) {
                builder.transition()
                        .from(IslFsmState.CLEAN_UP_RESOURCES).to(IslFsmState.OPERATIONAL).on(event)
                        .callMethod("resurrectNotification");
            }
            builder.transition()
                    .from(IslFsmState.CLEAN_UP_RESOURCES).to(IslFsmState.OPERATIONAL).on(IslFsmEvent.ROUND_TRIP_STATUS)
                    .when(new Condition<IslFsmContext>() {
                        @Override
                        public boolean isSatisfied(IslFsmContext context) {
                            RoundTripStatus status = context.getRoundTripStatus();
                            return status != null && !status.getStatus().equals(IslStatus.INACTIVE);
                        }

                        @Override
                        public String name() {
                            return "round-trip-status-is-not-fail";
                        }
                    })
                    .callMethod("resurrectNotification");
            builder.onEntry(IslFsmState.CLEAN_UP_RESOURCES).callMethod("cleanUpResourcesEnter");
            builder.internalTransition()
                    .within(IslFsmState.CLEAN_UP_RESOURCES).on(IslFsmEvent.ISL_RULE_REMOVED)
                    .callMethod("handleRemovedRule");
            builder.internalTransition()
                    .within(IslFsmState.CLEAN_UP_RESOURCES).on(IslFsmEvent.ISL_RULE_FAILED)
                    .callMethod("cleanUpResourcesFailed");

            // DELETED
            builder.defineFinalState(IslFsmState.DELETED);

            builder.onEntry(IslFsmState.DELETED)
                    .callMethod("deletedEnter");
        }

        public FsmExecutor<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> produceExecutor() {
            return new FsmExecutor<>(IslFsmEvent.NEXT);
        }

        /**
         * Create and properly initialize new {@link IslFsm}.
         */
        public IslFsm produce(NetworkOptions options, IslReference reference, IslFsmContext context) {
            IslFsm fsm = builder.newStateMachine(
                    IslFsmState.OPERATIONAL, clock, persistenceManager, dashboardLoggerBuilder.build(log),
                    options, reference);
            fsm.start(context);
            return fsm;
        }

        /**
         * Used FSM library (squirrel-foundation) have broken/corrupted internal transitions implementation for
         * composite states. That's why we need to define internal transitions for each nested state.
         *
         * <p>Before the fix for https://github.com/hekailiang/squirrel/issues/17 implementation was incorrect. The fix
         * was in disabling internal transitions for nested states (without any mention in the documentation). So the
         * implementation is completely corrupted now.
         */
        private static void setupOperationalInternalTransitions(
                StateMachineBuilder<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> builder, IslFsmState target) {
            final String action = "updateMonitorsAction";

            builder.internalTransition().within(target).on(IslFsmEvent.ISL_UP)
                    .callMethod(action);
            builder.internalTransition().within(target).on(IslFsmEvent.ISL_DOWN)
                    .callMethod(action);
            builder.internalTransition().within(target).on(IslFsmEvent.ISL_MOVE)
                    .callMethod(action);
            builder.internalTransition().within(target).on(IslFsmEvent.BFD_UP)
                    .callMethod(action);
            builder.internalTransition().within(target).on(IslFsmEvent.BFD_DOWN)
                    .callMethod(action);
            builder.internalTransition().within(target).on(IslFsmEvent.BFD_KILL)
                    .callMethod(action);
            builder.internalTransition().within(target).on(IslFsmEvent.BFD_FAIL)
                    .callMethod(action);
            builder.internalTransition().within(target).on(IslFsmEvent.ROUND_TRIP_STATUS)
                    .callMethod(action);

            builder.internalTransition().within(target).on(IslFsmEvent._FLUSH)
                    .callMethod("flushAction");
            builder.internalTransition().within(target).on(IslFsmEvent.ISL_REMOVE)
                    .callMethod("removeAttempt");
            builder.internalTransition().within(target).on(IslFsmEvent.BFD_PROPERTIES_UPDATE)
                    .callMethod("bfdPropertiesUpdateAction");
        }
    }

    @Value
    @Builder
    public static class IslFsmContext {
        private final IIslCarrier output;
        private final Endpoint endpoint;

        private IslDataHolder islData;

        private IslDownReason downReason;
        private Boolean bfdEnable;

        private RoundTripStatus roundTripStatus;

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
        _FLUSH,
        _BECOME_UP, _BECOME_DOWN, _BECOME_MOVED,
        _RESOURCES_DONE,
        _REMOVE_CONFIRMED,
        BFD_UP, BFD_DOWN, BFD_KILL, BFD_FAIL,
        BFD_PROPERTIES_UPDATE,
        ROUND_TRIP_STATUS,
        ISL_UP, ISL_DOWN, ISL_MOVE,
        ISL_REMOVE,

        ISL_RULE_INSTALLED, ISL_RULE_REMOVED, ISL_RULE_FAILED
    }

    public enum IslFsmState {
        OPERATIONAL,
        PENDING, ACTIVE, USABLE, INACTIVE, MOVED,
        SET_UP_RESOURCES, CLEAN_UP_RESOURCES,
        DELETED
    }
}
