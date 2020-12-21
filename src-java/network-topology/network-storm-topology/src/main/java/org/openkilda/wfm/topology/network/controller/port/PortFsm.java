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

package org.openkilda.wfm.topology.network.controller.port;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PortProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmContext;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmEvent;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmState;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;
import org.openkilda.wfm.topology.network.service.IPortCarrier;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

@Slf4j
public final class PortFsm extends AbstractBaseFsm<PortFsm, PortFsmState, PortFsmEvent,
        PortFsmContext> {
    private final Endpoint endpoint;
    private final Isl history;

    private final PortReportFsm reportFsm;
    private final PortPropertiesRepository portPropertiesRepository;

    public static PortFsmFactory factory(PersistenceManager persistenceManager) {
        return new PortFsmFactory(persistenceManager);
    }

    public PortFsm(PortReportFsm reportFsm, PortPropertiesRepository portPropertiesRepository, Endpoint endpoint,
                   Isl history) {
        this.reportFsm = reportFsm;
        this.portPropertiesRepository = portPropertiesRepository;
        this.endpoint = endpoint;
        this.history = history;
    }

    // -- FSM actions --

    public void setupUniIsl(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().setupUniIslHandler(endpoint, history);
    }

    public void upEnter(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        reportFsm.fire(PortFsmEvent.PORT_UP);
        boolean discoveryEnabled = portPropertiesRepository
                .getBySwitchIdAndPort(endpoint.getDatapath(), endpoint.getPortNumber())
                .map(PortProperties::isDiscoveryEnabled)
                .orElse(PortProperties.DISCOVERY_ENABLED_DEFAULT);
        if (discoveryEnabled) {
            context.getOutput().enableDiscoveryPoll(endpoint);
        }
    }

    public void enableDiscovery(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().enableDiscoveryPoll(endpoint);
    }

    public void upDisabledEnter(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
        output.notifyPortDiscoveryFailed(endpoint);
        emitRoundTripInactive(output);
    }

    public void proxyDiscovery(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().notifyPortDiscovered(endpoint, context.getSpeakerDiscoveryEvent());
    }

    public void proxyFail(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().notifyPortDiscoveryFailed(endpoint);
    }

    public void proxyRoundTripStatus(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().notifyPortRoundTripStatus(context.getRoundTripStatus());
    }

    public void downEnter(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        reportFsm.fire(PortFsmEvent.PORT_DOWN);
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
        output.notifyPortPhysicalDown(endpoint);
        emitRoundTripInactive(output);
    }

    public void regionMissingEnter(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
    }

    public void finish(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
        output.removeUniIslHandler(endpoint);
    }

    // -- private/service methods --

    private void emitRoundTripInactive(IPortCarrier carrier) {
        carrier.notifyPortRoundTripStatus(new RoundTripStatus(endpoint, IslStatus.INACTIVE));
    }

    // -- service data types --

    public static class PortFsmFactory {
        private final StateMachineBuilder<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> builder;
        private final PortPropertiesRepository portPropertiesRepository;

        PortFsmFactory(PersistenceManager persistenceManager) {
            portPropertiesRepository = persistenceManager.getRepositoryFactory().createPortPropertiesRepository();

            final String proxyRoundTripStatusMethod = "proxyRoundTripStatus";

            builder = StateMachineBuilderFactory.create(
                    PortFsm.class, PortFsmState.class, PortFsmEvent.class, PortFsmContext.class,
                    // extra parameters
                    PortReportFsm.class, PortPropertiesRepository.class, Endpoint.class, Isl.class);

            // INIT
            builder.transition()
                    .from(PortFsmState.INIT).to(PortFsmState.OPERATIONAL).on(PortFsmEvent.ONLINE);
            builder.transition()
                    .from(PortFsmState.INIT).to(PortFsmState.UNOPERATIONAL).on(PortFsmEvent.OFFLINE);
            builder.onEntry(PortFsmState.INIT)
                    .callMethod("setupUniIsl");

            // OPERATIONAL
            builder.transition()
                    .from(PortFsmState.OPERATIONAL).to(PortFsmState.UNOPERATIONAL).on(PortFsmEvent.OFFLINE);
            builder.transition()
                    .from(PortFsmState.OPERATIONAL).to(PortFsmState.FINISH).on(PortFsmEvent.PORT_DEL);
            builder.transition()
                    .from(PortFsmState.OPERATIONAL).to(PortFsmState.REGION_MISSING).on(PortFsmEvent.REGION_OFFLINE);
            builder.defineSequentialStatesOn(PortFsmState.OPERATIONAL,
                    PortFsmState.UNKNOWN, PortFsmState.UP, PortFsmState.UP_DISABLED, PortFsmState.DOWN);

            // UNOPERATIONAL
            builder.transition()
                    .from(PortFsmState.UNOPERATIONAL).to(PortFsmState.OPERATIONAL).on(PortFsmEvent.ONLINE);
            builder.transition()
                    .from(PortFsmState.UNOPERATIONAL).to(PortFsmState.FINISH).on(PortFsmEvent.PORT_DEL);
            builder.internalTransition().within(PortFsmState.UNOPERATIONAL).on(PortFsmEvent.FAIL)
                    .callMethod("proxyFail");
            builder.internalTransition().within(PortFsmState.UNOPERATIONAL).on(PortFsmEvent.ROUND_TRIP_STATUS)
                    .callMethod(proxyRoundTripStatusMethod);

            // REGION_MISSING
            builder.transition()
                    .from(PortFsmState.REGION_MISSING).to(PortFsmState.OPERATIONAL).on(PortFsmEvent.ONLINE);
            builder.transition()
                    .from(PortFsmState.REGION_MISSING).to(PortFsmState.FINISH).on(PortFsmEvent.PORT_DEL);
            builder.onEntry(PortFsmState.REGION_MISSING)
                    .callMethod("regionMissingEnter");

            // UNKNOWN
            builder.transition()
                    .from(PortFsmState.UNKNOWN).to(PortFsmState.UP).on(PortFsmEvent.PORT_UP);
            builder.transition()
                    .from(PortFsmState.UNKNOWN).to(PortFsmState.DOWN).on(PortFsmEvent.PORT_DOWN);

            // UP
            builder.transition()
                    .from(PortFsmState.UP).to(PortFsmState.DOWN).on(PortFsmEvent.PORT_DOWN);
            builder.transition()
                    .from(PortFsmState.UP).to(PortFsmState.UP_DISABLED).on(PortFsmEvent.DISABLE_DISCOVERY);
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.DISCOVERY)
                    .callMethod("proxyDiscovery");
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.FAIL)
                    .callMethod("proxyFail");
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.ROUND_TRIP_STATUS)
                    .callMethod(proxyRoundTripStatusMethod);
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.ENABLE_DISCOVERY)
                    .callMethod("enableDiscovery");
            builder.onEntry(PortFsmState.UP)
                    .callMethod("upEnter");

            // UP_DISABLED
            builder.transition()
                    .from(PortFsmState.UP_DISABLED).to(PortFsmState.DOWN).on(PortFsmEvent.PORT_DOWN);
            builder.transition()
                    .from(PortFsmState.UP_DISABLED).to(PortFsmState.UP).on(PortFsmEvent.ENABLE_DISCOVERY);
            builder.onEntry(PortFsmState.UP_DISABLED)
                    .callMethod("upDisabledEnter");

            // DOWN
            builder.transition()
                    .from(PortFsmState.DOWN).to(PortFsmState.UP).on(PortFsmEvent.PORT_UP);
            builder.internalTransition().within(PortFsmState.DOWN).on(PortFsmEvent.FAIL)
                    .callMethod("proxyFail");
            builder.onEntry(PortFsmState.DOWN)
                    .callMethod("downEnter");

            // FINISH
            builder.onEntry(PortFsmState.FINISH)
                    .callMethod("finish");
        }

        public FsmExecutor<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> produceExecutor() {
            return new FsmExecutor<>(PortFsmEvent.NEXT);
        }

        public PortFsm produce(PortReportFsm.PortReportFsmFactory reportFactory, Endpoint endpoint, Isl history) {
            return builder.newStateMachine(PortFsmState.INIT, reportFactory.produce(endpoint),
                    portPropertiesRepository, endpoint, history);
        }
    }

    @Value
    @Builder
    public static class PortFsmContext {
        private final IPortCarrier output;

        private Isl history;
        private IslInfoData speakerDiscoveryEvent;
        private RoundTripStatus roundTripStatus;

        public static PortFsmContextBuilder builder(IPortCarrier output) {
            return new PortFsmContextBuilder()
                    .output(output);
        }
    }

    public enum PortFsmEvent {
        NEXT,

        ONLINE, OFFLINE, REGION_OFFLINE,
        PORT_UP, PORT_DOWN, PORT_DEL,
        ENABLE_DISCOVERY, DISABLE_DISCOVERY,
        DISCOVERY, FAIL, ROUND_TRIP_STATUS
    }

    public enum PortFsmState {
        INIT,

        OPERATIONAL,
        UNKNOWN, UP, UP_DISABLED, DOWN,

        FINISH, UNOPERATIONAL, REGION_MISSING
    }
}
