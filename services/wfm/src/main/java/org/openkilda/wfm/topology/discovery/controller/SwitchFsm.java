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

package org.openkilda.wfm.topology.discovery.controller;

import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.facts.BfdPortFacts;
import org.openkilda.wfm.topology.discovery.model.facts.HistoryFacts;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts.LinkStatus;
import org.openkilda.wfm.topology.discovery.service.ISwitchCarrier;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public final class SwitchFsm extends AbstractStateMachine<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> {
    private final TransactionManager transactionManager;
    private final SwitchRepository switchRepository;

    private final SwitchId switchId;
    private final Integer bfdLocalPortOffset;

    private final Map<Integer, PortFacts> portByNumber = new HashMap<>();

    private static final StateMachineBuilder<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                SwitchFsm.class, SwitchFsmState.class, SwitchFsmEvent.class, SwitchFsmContext.class,
                // extra parameters
                PersistenceManager.class, SwitchId.class, Integer.class);

        // INIT
        builder.transition()
                .from(SwitchFsmState.INIT).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.HISTORY)
                .callMethod("applyHistory");
        builder.transition()
                .from(SwitchFsmState.INIT).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);

        // SETUP
        builder.transition()
                .from(SwitchFsmState.SETUP).to(SwitchFsmState.ONLINE).on(SwitchFsmEvent.NEXT);
        builder.onEntry(SwitchFsmState.SETUP)
                .callMethod("setupEnter");

        // ONLINE
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
        builder.transition().from(SwitchFsmState.OFFLINE).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);
        builder.onEntry(SwitchFsmState.OFFLINE)
                .callMethod("offlineEnter");
    }

    public static FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> makeExecutor() {
        return new FsmExecutor<>(SwitchFsmEvent.NEXT);
    }

    public static SwitchFsm create(PersistenceManager persistenceManager, SwitchId switchId,
                                   Integer bfdLocalPortOffset) {
        return builder.newStateMachine(SwitchFsmState.INIT, persistenceManager, switchId, bfdLocalPortOffset);
    }

    private SwitchFsm(PersistenceManager persistenceManager, SwitchId switchId, Integer bfdLocalPortOffset) {
        this.transactionManager = persistenceManager.getTransactionManager();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        this.switchId = switchId;
        this.bfdLocalPortOffset = bfdLocalPortOffset;
    }

    // -- FSM actions --

    private void applyHistory(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        HistoryFacts historyFacts = context.getHistory();
        for (Isl outgoingLink : historyFacts.getOutgoingLinks()) {
            portAdd(context, outgoingLink);
        }
    }

    private void setupEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        SpeakerSwitchView speakerData = context.getSpeakerData();

        Set<Integer> removedPorts = new HashSet<>(portByNumber.keySet());
        List<PortFacts> becomeUpPorts = new ArrayList<>();
        List<PortFacts> becomeDownPorts = new ArrayList<>();
        for (SpeakerSwitchPortView port : speakerData.getPorts()) {
            removedPorts.remove(port.getNumber());

            PortFacts actualPort = new PortFacts(switchId, port);
            if (!portByNumber.containsKey(port.getNumber())) {
                // port added
                portAdd(context, actualPort);
            }

            switch (actualPort.getLinkStatus()) {
                case UP:
                    becomeUpPorts.add(actualPort);
                    break;
                case DOWN:
                    becomeDownPorts.add(actualPort);
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Unsupported port admin state value %s (%s)",
                            actualPort.getLinkStatus(), actualPort.getLinkStatus().getClass().getName()));
            }
        }

        for (Integer portNumber : removedPorts) {
            portDel(context, portNumber);
        }

        // emit "online" status for all ports
        for (PortFacts port : portByNumber.values()) {
            updateOnlineStatus(context, port.getEndpoint(), true);
        }

        for (PortFacts port : becomeDownPorts) {
            port.setLinkStatus(LinkStatus.DOWN);
            updatePortLinkMode(context, port);
        }

        for (PortFacts port : becomeUpPorts) {
            port.setLinkStatus(LinkStatus.UP);
            updatePortLinkMode(context, port);
        }
    }

    private void onlineEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        transactionManager.doInTransaction(() -> persistSwitchData(context));
        initialSwitchSetup(context);
    }

    private void offlineEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        transactionManager.doInTransaction(() -> updatePersistentStatus(SwitchStatus.INACTIVE));

        ISwitchCarrier output = context.getOutput();
        for (PortFacts port : portByNumber.values()) {
            updateOnlineStatus(context, port.getEndpoint(), false);
        }
    }

    private void handlePortAdd(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        PortFacts port = new PortFacts(Endpoint.of(switchId, context.getPortNumber()));
        portAdd(context, port);
    }

    private void handlePortDel(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        portDel(context, context.getPortNumber());
    }

    private void handlePortLinkStateChange(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                           SwitchFsmContext context) {
        PortFacts port = portByNumber.get(context.getPortNumber());
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

        updatePortLinkMode(context, port);
    }

    // -- private/service methods --

    private void portAdd(SwitchFsmContext context, PortFacts portFacts) {
        portAdd(context, portFacts, null);
    }

    private void portAdd(SwitchFsmContext context, Isl history) {
        Endpoint endpoint = Endpoint.of(switchId, history.getSrcPort());
        PortFacts portFacts = new PortFacts(endpoint);
        portAdd(context, portFacts, history);
    }

    private void portAdd(SwitchFsmContext context, PortFacts portFacts, Isl history) {
        portByNumber.put(portFacts.getPortNumber(), portFacts);

        if (isBfdLogicalPort(portFacts.getPortNumber())) {
            context.getOutput().setupBfdPortHandler(
                    new BfdPortFacts(portFacts, portFacts.getPortNumber() - bfdLocalPortOffset));
        } else {
            context.getOutput().setupPortHandler(portFacts, history);
        }
    }

    private void portDel(SwitchFsmContext context, int portNumber) {
        portByNumber.remove(portNumber);
        Endpoint endpoint = Endpoint.of(switchId, portNumber);
        if (isBfdLogicalPort(portNumber)) {
            context.getOutput().removeBfdPortHandler(endpoint);
        } else {
            context.getOutput().removePortHandler(endpoint);
        }
    }

    private void updatePortLinkMode(SwitchFsmContext context, PortFacts portFacts) {
        if (isBfdLogicalPort(portFacts.getPortNumber())) {
            context.getOutput().setBfdPortLinkMode(portFacts);
        } else {
            context.getOutput().setPortLinkMode(portFacts);
        }
    }

    private void updateOnlineStatus(SwitchFsmContext context, Endpoint endpoint, boolean mode) {
        if (isBfdLogicalPort(endpoint.getPortNumber())) {
            context.getOutput().setBfdPortOnlineMode(endpoint, mode);
        } else {
            context.getOutput().setOnlineMode(endpoint, mode);
        }
    }

    private void persistSwitchData(SwitchFsmContext context) {
        Switch sw = switchRepository.findById(switchId)
                .orElseGet(() -> Switch.builder().switchId(switchId).build());

        SpeakerSwitchView speakerData = context.getSpeakerData();
        sw.setAddress(speakerData.getSwitchSocketAddress().toString());
        sw.setHostname(speakerData.getSwitchSocketAddress().getHostName());

        SpeakerSwitchDescription description = speakerData.getDescription();
        sw.setDescription(String.format("%s %s %s",
                                     description.getManufacturer(),
                                     speakerData.getOfVersion(),
                                     description.getSoftware()));
        // TODO(surabujin): grab changes from PR2039

        sw.setStatus(SwitchStatus.ACTIVE);

        switchRepository.createOrUpdate(sw);
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

    private boolean isBfdLogicalPort(int portNumber) {
        return bfdLocalPortOffset < portNumber;
    }
}
