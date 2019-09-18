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
import org.openkilda.model.PortProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmContext;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmEvent;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmState;
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

    public static PortFsmFactory factory() {
        return new PortFsmFactory();
    }

    public PortFsm(PortReportFsm reportFsm, PersistenceManager persistenceManager, Endpoint endpoint, Isl history) {
        this.reportFsm = reportFsm;
        this.portPropertiesRepository = persistenceManager.getRepositoryFactory().createPortPropertiesRepository();
        this.endpoint = endpoint;
        this.history = history;
    }

    // -- FSM actions --

    public void setupUniIsl(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().setupUniIslHandler(endpoint, history);
    }

    public void upEnter(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        reportFsm.fire(PortFsmEvent.PORT_UP);
        PortProperties portProperties
                = portPropertiesRepository.getBySwitchIdAndPort(endpoint.getDatapath(), endpoint.getPortNumber());
        if (portProperties.isDiscoveryEnabled()) {
            context.getOutput().enableDiscoveryPoll(endpoint);
        }
    }

    public void enableDiscovery(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().enableDiscoveryPoll(endpoint);
    }

    public void disableDiscovery(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
        output.notifyPortDiscoveryFailed(endpoint);
    }

    public void proxyDiscovery(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().notifyPortDiscovered(endpoint, context.getSpeakerDiscoveryEvent());
    }

    public void proxyFail(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().notifyPortDiscoveryFailed(endpoint);
    }

    public void downEnter(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        reportFsm.fire(PortFsmEvent.PORT_DOWN);
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
        output.notifyPortPhysicalDown(endpoint);
    }

    public void finish(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
        output.removeUniIslHandler(endpoint);
    }

    // -- private/service methods --

    // -- service data types --

    public static class PortFsmFactory {
        private final StateMachineBuilder<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> builder;

        PortFsmFactory() {
            builder = StateMachineBuilderFactory.create(
                    PortFsm.class, PortFsmState.class, PortFsmEvent.class, PortFsmContext.class,
                    // extra parameters
                    PortReportFsm.class, PersistenceManager.class, Endpoint.class, Isl.class);

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
            builder.defineSequentialStatesOn(PortFsmState.OPERATIONAL,
                    PortFsmState.UNKNOWN, PortFsmState.UP, PortFsmState.DOWN);

            // UNOPERATIONAL
            builder.transition()
                    .from(PortFsmState.UNOPERATIONAL).to(PortFsmState.OPERATIONAL).on(PortFsmEvent.ONLINE);
            builder.transition()
                    .from(PortFsmState.UNOPERATIONAL).to(PortFsmState.FINISH).on(PortFsmEvent.PORT_DEL);
            builder.internalTransition().within(PortFsmState.UNOPERATIONAL).on(PortFsmEvent.FAIL)
                    .callMethod("proxyFail");

            // UNKNOWN
            builder.transition()
                    .from(PortFsmState.UNKNOWN).to(PortFsmState.UP).on(PortFsmEvent.PORT_UP);
            builder.transition()
                    .from(PortFsmState.UNKNOWN).to(PortFsmState.DOWN).on(PortFsmEvent.PORT_DOWN);

            // UP
            builder.transition()
                    .from(PortFsmState.UP).to(PortFsmState.DOWN).on(PortFsmEvent.PORT_DOWN);
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.DISCOVERY)
                    .callMethod("proxyDiscovery");
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.FAIL)
                    .callMethod("proxyFail");
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.ENABLE_DISCOVERY)
                    .callMethod("enableDiscovery");
            builder.internalTransition().within(PortFsmState.UP).on(PortFsmEvent.DISABLE_DISCOVERY)
                    .callMethod("disableDiscovery");
            builder.onEntry(PortFsmState.UP)
                    .callMethod("upEnter");

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

        public PortFsm produce(PortReportFsm.PortReportFsmFactory reportFactory,
                               PersistenceManager persistenceManager, Endpoint endpoint, Isl history) {
            return builder.newStateMachine(PortFsmState.INIT, reportFactory.produce(endpoint),
                    persistenceManager, endpoint, history);
        }
    }

    @Value
    @Builder
    public static class PortFsmContext {
        private final IPortCarrier output;

        private Isl history;
        private IslInfoData speakerDiscoveryEvent;

        public static PortFsmContextBuilder builder(IPortCarrier output) {
            return new PortFsmContextBuilder()
                    .output(output);
        }
    }

    public enum PortFsmEvent {
        NEXT,

        ONLINE, OFFLINE,
        PORT_UP, PORT_DOWN, PORT_DEL,
        ENABLE_DISCOVERY, DISABLE_DISCOVERY,
        DISCOVERY, FAIL
    }

    public enum PortFsmState {
        INIT,

        OPERATIONAL,
        UNKNOWN, UP, DOWN,

        FINISH, UNOPERATIONAL
    }
}
