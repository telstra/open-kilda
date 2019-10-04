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

package org.openkilda.wfm.topology.network.controller.sw;

import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.controller.sw.SwitchFsm.SwitchFsmContext;
import org.openkilda.wfm.topology.network.controller.sw.SwitchFsm.SwitchFsmEvent;
import org.openkilda.wfm.topology.network.controller.sw.SwitchFsm.SwitchFsmState;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.facts.HistoryFacts;
import org.openkilda.wfm.topology.network.service.ISwitchCarrier;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class SwitchFsm extends AbstractBaseFsm<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> {

    private final NetworkTopologyDashboardLogger logWrapper = new NetworkTopologyDashboardLogger(log);

    private final TransactionManager transactionManager;
    private final RetryPolicy transactionRetryPolicy;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final SwitchId switchId;

    private final Set<SwitchFeature> features = new HashSet<>();
    private final Map<Integer, AbstractPort> portByNumber = new HashMap<>();

    private final NetworkOptions options;

    private int syncAttempts;
    private String awaitingResponseKey;

    private SpeakerSwitchView speakerData;

    public static SwitchFsmFactory factory() {
        return new SwitchFsmFactory();
    }

    public SwitchFsm(PersistenceManager persistenceManager, SwitchId switchId, NetworkOptions options) {
        this.transactionManager = persistenceManager.getTransactionManager();
        this.transactionRetryPolicy = transactionManager.makeRetryPolicyBlank()
                .withMaxDuration(options.getDbRepeatMaxDurationSeconds(), TimeUnit.SECONDS);
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        this.switchId = switchId;
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.kildaConfigurationRepository = persistenceManager.getRepositoryFactory()
                .createKildaConfigurationRepository();

        this.options = options;
    }

    // -- FSM actions --

    public void applyHistory(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                             SwitchFsmContext context) {
        HistoryFacts historyFacts = context.getHistory();
        for (Isl outgoingLink : historyFacts.getOutgoingLinks()) {
            PhysicalPort port = new PhysicalPort(Endpoint.of(switchId, outgoingLink.getSrcPort()));
            port.setHistory(outgoingLink);
            portAdd(port, context);
        }
    }

    public void syncEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        speakerData = context.getSpeakerData();
        transactionManager.doInTransaction(transactionRetryPolicy, this::persistSwitchData);
        syncAttempts = options.getCountSynchronizationAttempts();
        performActionsDependingOnAttemptsCount(context);
    }

    public void syncPerformed(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        log.debug("Received sync response \"{}\" for switch {}", context.getSyncResponse(), switchId);
        processSyncResponse(context);
    }

    public void syncError(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        log.debug("Received sync error response for switch {}", switchId);
        processSyncResponse(context);
    }

    public void syncTimeout(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        log.debug("Received sync timeout for switch {}", switchId);
        processSyncResponse(context);
    }

    public void setupEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        logWrapper.onSwitchUpdateStatus(switchId, NetworkTopologyDashboardLogger.SwitchState.ONLINE);

        updatePorts(context, speakerData, true);
        speakerData = null;
    }

    public void onlineEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        initialSwitchSetup(context);
    }

    public void offlineEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                             SwitchFsmContext context) {
        logWrapper.onSwitchUpdateStatus(switchId, NetworkTopologyDashboardLogger.SwitchState.OFFLINE);
        transactionManager.doInTransaction(transactionRetryPolicy, () -> updatePersistentStatus(SwitchStatus.INACTIVE));

        for (AbstractPort port : portByNumber.values()) {
            updateOnlineStatus(port, context, false);
        }
    }

    public void syncState(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        updatePorts(context, context.getSpeakerData(), false);
    }

    public void handlePortAdd(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                              SwitchFsmContext context) {
        AbstractPort port = makePortRecord(Endpoint.of(switchId, context.getPortNumber()));
        log.info("Receive port-add notification for {}", port);

        portAdd(port, context);
        updateOnlineStatus(port, context, true);

        Boolean isPortEnabled = context.getPortEnabled();
        if (isPortEnabled == null) {
            log.error("Link status of {} is unknown - treat is as DOWN", port.getEndpoint());
            isPortEnabled = false;
        }
        port.setLinkStatus(isPortEnabled ? LinkStatus.UP : LinkStatus.DOWN);
        updatePortLinkMode(port, context);
    }

    public void handlePortDel(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                              SwitchFsmContext context) {
        AbstractPort port = portByNumber.get(context.getPortNumber());
        if (port != null) {
            portDel(port, context);
        } else {
            log.error("Receive port del request for {}, but this port is missing",
                    Endpoint.of(switchId, context.getPortNumber()));
        }
    }

    public void handlePortLinkStateChange(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                          SwitchFsmContext context) {
        AbstractPort port = portByNumber.get(context.getPortNumber());
        if (port == null) {
            log.error("Port {} is not listed into {}", context.getPortNumber(), switchId);
            return;
        }

        switch (event) {
            case PORT_UP:
                port.setLinkStatus(LinkStatus.UP);
                break;
            case PORT_DOWN:
                port.setLinkStatus(LinkStatus.DOWN);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unexpected event %s received in state %s (%s)",
                                                                 event, to, getClass().getName()));
        }

        updatePortLinkMode(port, context);
    }

    /**
     * Removed ports FSM on SWITCH_REMOVE event.
     */
    public void removePortsFsm(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                               SwitchFsmContext context) {
        List<AbstractPort> ports = new ArrayList<>(portByNumber.values());
        for (AbstractPort port : ports) {
            portDel(port, context);
        }
    }

    // -- private/service methods --

    private void processSyncResponse(SwitchFsmContext context) {
        if (isAwaitingResponse(context.getSyncKey())) {
            SwitchSyncResponse syncResponse = context.getSyncResponse();
            if (syncResponse != null && isSynchronized(syncResponse)) {
                fire(SwitchFsmEvent.SYNC_ENDED, context);
            } else {
                syncAttempts--;
                performActionsDependingOnAttemptsCount(context);
            }
        }
    }

    private boolean isSynchronized(SwitchSyncResponse syncResponse) {
        RulesSyncEntry rules = syncResponse.getRules();
        boolean missingRulesCheck = missingRulesCheck(rules);
        boolean misconfiguredRulesCheck = misconfiguredRulesCheck(rules);
        boolean excessRulesCheck = excessRulesCheck(rules);

        boolean missingMetersCheck = true;
        boolean excessMetersCheck = true;
        MetersSyncEntry meters = syncResponse.getMeters();
        if (meters != null) {
            missingMetersCheck = missingMetersCheck(meters);
            excessMetersCheck = excessMetersCheck(meters);
        }

        return missingRulesCheck && misconfiguredRulesCheck && excessRulesCheck
                && missingMetersCheck && excessMetersCheck;
    }

    private boolean missingRulesCheck(RulesSyncEntry rules) {
        return rules.getMissing().isEmpty() || rules.getInstalled().containsAll(rules.getMissing());
    }

    private boolean misconfiguredRulesCheck(RulesSyncEntry rules) {
        return rules.getMisconfigured().isEmpty()
                || (rules.getInstalled().containsAll(rules.getMisconfigured())
                && rules.getRemoved().containsAll(rules.getMisconfigured()));
    }

    private boolean excessRulesCheck(RulesSyncEntry rules) {
        return !options.isRemoveExcessWhenSwitchSync()
                || rules.getExcess().isEmpty() || rules.getRemoved().containsAll(rules.getExcess());
    }

    private boolean missingMetersCheck(MetersSyncEntry meters) {
        return meters.getMissing().isEmpty() || meters.getInstalled().containsAll(meters.getMissing());
    }

    private boolean excessMetersCheck(MetersSyncEntry meters) {
        return !options.isRemoveExcessWhenSwitchSync()
                || meters.getExcess().isEmpty() || meters.getRemoved().containsAll(meters.getExcess());
    }

    private void performActionsDependingOnAttemptsCount(SwitchFsmContext context) {
        if (syncAttempts <= 0) {
            fire(SwitchFsmEvent.SYNC_ENDED, context);
        } else {
            initSync(context);
        }
    }

    private void initSync(SwitchFsmContext context) {
        awaitingResponseKey = UUID.randomUUID().toString();
        context.getOutput().sendSwitchSynchronizeRequest(awaitingResponseKey, switchId);
    }

    private boolean isAwaitingResponse(String key) {
        return Objects.equals(awaitingResponseKey, key);
    }

    private void updatePorts(SwitchFsmContext context, SpeakerSwitchView speakerData, boolean isForced) {
        // set features for correct port (re)identification
        updateFeatures(speakerData.getFeatures());

        Set<Integer> removedPorts = new HashSet<>(portByNumber.keySet());
        for (SpeakerSwitchPortView speakerPort : speakerData.getPorts()) {
            AbstractPort actualPort = makePortRecord(speakerPort);
            AbstractPort storedPort = portByNumber.get(speakerPort.getNumber());

            boolean isNewOrRecreated = true;
            if (storedPort == null) {
                storedPort = portAdd(actualPort, context);
            } else if (!storedPort.isSameKind(actualPort)) {
                // port kind have been changed, we must recreate port handler
                portDel(storedPort, context);
                storedPort = portAdd(actualPort, context);
            } else {
                removedPorts.remove(speakerPort.getNumber());
                isNewOrRecreated = false;
            }

            if (isForced || isNewOrRecreated) {
                updateOnlineStatus(storedPort, context, true);
                updatePortLinkMode(storedPort, actualPort.getLinkStatus(), context);
            } else if (storedPort.getLinkStatus() != actualPort.getLinkStatus()) {
                updatePortLinkMode(storedPort, actualPort.getLinkStatus(), context);
            }
        }

        for (Integer portNumber : removedPorts) {
            portDel(portByNumber.get(portNumber), context);
        }
    }

    private void updateFeatures(Set<SwitchFeature> update) {
        if (!features.equals(update)) {
            log.info("Update features for {} - old:{} new:{}", switchId, features, update);
            features.clear();
            features.addAll(update);
        }
    }

    private AbstractPort portAdd(AbstractPort port, SwitchFsmContext context) {
        portByNumber.put(port.getPortNumber(), port);
        port.portAdd(context.getOutput());
        logWrapper.onPortAdd(switchId, port);

        return port;
    }

    private void portDel(AbstractPort port, SwitchFsmContext context) {
        portByNumber.remove(port.getPortNumber());
        port.portDel(context.getOutput());
        logWrapper.onPortDelete(switchId, port);
    }

    private void updatePortLinkMode(AbstractPort port, LinkStatus status, SwitchFsmContext context) {
        port.setLinkStatus(status);
        updatePortLinkMode(port, context);
    }

    private void updatePortLinkMode(AbstractPort port, SwitchFsmContext context) {
        port.updatePortLinkMode(context.getOutput());
    }

    private void updateOnlineStatus(AbstractPort port, SwitchFsmContext context, boolean mode) {
        port.updateOnlineStatus(context.getOutput(), mode);
    }

    private void persistSwitchData() {
        Switch sw = switchRepository.findById(switchId)
                .orElseGet(() -> Switch.builder().switchId(switchId).build());

        InetSocketAddress socketAddress = speakerData.getSwitchSocketAddress();

        sw.setSocketAddress(socketAddress);
        sw.setHostname(socketAddress.getHostName());

        SpeakerSwitchDescription description = speakerData.getDescription();
        sw.setDescription(String.format("%s %s %s",
                description.getManufacturer(),
                speakerData.getOfVersion(),
                description.getSoftware()));

        sw.setOfVersion(speakerData.getOfVersion());
        sw.setOfDescriptionManufacturer(description.getManufacturer());
        sw.setOfDescriptionHardware(description.getHardware());
        sw.setOfDescriptionSoftware(description.getSoftware());
        sw.setOfDescriptionSerialNumber(description.getSerialNumber());
        sw.setOfDescriptionDatapath(description.getDatapath());

        sw.setStatus(SwitchStatus.ACTIVE);

        sw.setFeatures(speakerData.getFeatures());

        persistSwitchProperties(sw);
        switchRepository.createOrUpdate(sw);
    }

    private void persistSwitchProperties(Switch sw) {
        boolean multiTable = kildaConfigurationRepository.get().isUseMultiTable()
                && features.contains(SwitchFeature.MULTI_TABLE);
        Optional<SwitchProperties> switchPropertiesResult = switchPropertiesRepository.findBySwitchId(sw.getSwitchId());
        SwitchProperties switchProperties = switchPropertiesResult.orElseGet(() ->
                SwitchProperties.builder()
                        .switchObj(sw)
                        .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                        .multiTable(multiTable)
                        .build());
        switchPropertiesRepository.createOrUpdate(switchProperties);
    }

    private void updatePersistentStatus(SwitchStatus status) {
        switchRepository.findById(switchId)
                .ifPresent(entry -> {
                    entry.setStatus(status);
                    switchRepository.createOrUpdate(entry);
                });
    }

    private void initialSwitchSetup(SwitchFsmContext context) {
        // FIXME(surabujin): move initial switch setup here (from FL)
    }

    private AbstractPort makePortRecord(SpeakerSwitchPortView speakerPort) {
        AbstractPort port = makePortRecord(Endpoint.of(switchId, speakerPort.getNumber()));
        port.setLinkStatus(LinkStatus.of(speakerPort.getState()));
        return port;
    }

    private AbstractPort makePortRecord(Endpoint endpoint) {
        AbstractPort record;
        if (isPhysicalPort(endpoint.getPortNumber())) {
            record = new PhysicalPort(endpoint);
        } else {
            // at this moment we know only 2 kind of ports - physical and logical-BFD
            record = new LogicalBfdPort(endpoint, endpoint.getPortNumber() - options.getBfdLogicalPortOffset());
        }

        return record;
    }

    /**
     * Distinguish physical ports from other port types.
     *
     * <p>At this moment we have 2 kind of ports - physical ports and logical-BFD ports. So if this method return false
     * wee have a deal with logical-BFD port.
     */
    private boolean isPhysicalPort(int portNumber) {
        if (features.contains(SwitchFeature.BFD)) {
            return portNumber < options.getBfdLogicalPortOffset();
        }
        return true;
    }

    // -- service data types --

    public static class SwitchFsmFactory {
        private final StateMachineBuilder<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> builder;

        SwitchFsmFactory() {
            builder = StateMachineBuilderFactory.create(
                    SwitchFsm.class, SwitchFsmState.class, SwitchFsmEvent.class, SwitchFsmContext.class,
                    // extra parameters
                    PersistenceManager.class, SwitchId.class, NetworkOptions.class);

            // INIT
            builder.transition()
                    .from(SwitchFsmState.INIT).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.HISTORY)
                    .callMethod("applyHistory");
            builder.transition().from(SwitchFsmState.INIT).to(SwitchFsmState.SYNC).on(SwitchFsmEvent.ONLINE);

            // SYNCHRONIZE
            builder.transition().from(SwitchFsmState.SYNC).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.SYNC_ENDED);
            builder.transition().from(SwitchFsmState.SYNC).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.OFFLINE);
            builder.internalTransition().within(SwitchFsmState.SYNC).on(SwitchFsmEvent.SYNC_RESPONSE)
                    .callMethod("syncPerformed");
            builder.internalTransition().within(SwitchFsmState.SYNC).on(SwitchFsmEvent.SYNC_ERROR)
                    .callMethod("syncError");
            builder.internalTransition().within(SwitchFsmState.SYNC).on(SwitchFsmEvent.SYNC_TIMEOUT)
                    .callMethod("syncTimeout");
            builder.onEntry(SwitchFsmState.SYNC)
                    .callMethod("syncEnter");

            // SETUP
            builder.transition()
                    .from(SwitchFsmState.SETUP).to(SwitchFsmState.ONLINE).on(SwitchFsmEvent.NEXT);
            builder.onEntry(SwitchFsmState.SETUP)
                    .callMethod("setupEnter");

            // ONLINE
            builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.ONLINE)
                    .callMethod("syncState");
            builder.transition().from(SwitchFsmState.ONLINE).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.OFFLINE);
            builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_ADD)
                    .callMethod("handlePortAdd");
            builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_DEL)
                    .callMethod("handlePortDel");
            builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_UP)
                    .callMethod("handlePortLinkStateChange");
            builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_DOWN)
                    .callMethod("handlePortLinkStateChange");
            builder.onEntry(SwitchFsmState.ONLINE)
                    .callMethod("onlineEnter");

            // OFFLINE
            builder.transition().from(SwitchFsmState.OFFLINE).to(SwitchFsmState.SYNC).on(SwitchFsmEvent.ONLINE);
            builder.transition().from(SwitchFsmState.OFFLINE).to(SwitchFsmState.DELETED)
                    .on(SwitchFsmEvent.SWITCH_REMOVE)
                    .callMethod("removePortsFsm");
            builder.onEntry(SwitchFsmState.OFFLINE)
                    .callMethod("offlineEnter");

            // DELETED
            builder.defineFinalState(SwitchFsmState.DELETED);
        }

        public FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> produceExecutor() {
            return new FsmExecutor<>(SwitchFsmEvent.NEXT);
        }

        public SwitchFsm produce(PersistenceManager persistenceManager, SwitchId switchId, NetworkOptions options) {
            return builder.newStateMachine(SwitchFsmState.INIT, persistenceManager, switchId, options);
        }
    }

    @Value
    @Builder(toBuilder = true)
    public static class SwitchFsmContext {
        private final ISwitchCarrier output;

        private SpeakerSwitchView speakerData;
        private HistoryFacts history;
        private SwitchSyncResponse syncResponse;
        private String syncKey;

        private Integer portNumber;
        private Boolean portEnabled;

        public static SwitchFsmContextBuilder builder(ISwitchCarrier output) {
            return (new SwitchFsmContextBuilder()).output(output);
        }
    }

    public enum SwitchFsmEvent {
        NEXT,

        HISTORY,

        SYNC_ENDED,
        SYNC_RESPONSE,
        SYNC_ERROR,
        SYNC_TIMEOUT,

        ONLINE,
        OFFLINE,

        PORT_ADD, PORT_DEL, PORT_UP, SWITCH_REMOVE, PORT_DOWN
    }

    public enum SwitchFsmState {
        INIT,
        SYNC,
        OFFLINE,
        ONLINE,
        SETUP,
        DELETED
    }
}
