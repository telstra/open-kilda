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

import static java.lang.String.format;

import org.openkilda.wfm.share.history.model.PortHistoryEvent;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmEvent;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmState;
import org.openkilda.wfm.topology.network.service.IPortCarrier;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.annotation.ContextInsensitive;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.time.Instant;

@Slf4j
@ContextInsensitive
public final class PortReportFsm extends AbstractStateMachine<PortReportFsm, PortFsmState, PortFsmEvent, Void> {

    private final NetworkTopologyDashboardLogger dashboardLogger;

    private final Endpoint endpoint;
    private final IPortCarrier carrier;

    public static PortReportFsmFactory factory(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder,
                                               IPortCarrier carrier) {
        return new PortReportFsmFactory(dashboardLoggerBuilder, carrier);
    }

    private PortReportFsm(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuider, Endpoint endpoint,
                          IPortCarrier carrier) {
        this.dashboardLogger = dashboardLoggerBuider.build(log);
        this.endpoint = endpoint;
        this.carrier = carrier;
    }

    // -- FSM actions --

    public void becomeUp(PortFsmState from, PortFsmState to, PortFsmEvent event) {
        dashboardLogger.onPortUp(endpoint);
        sendPortHistory(event, carrier);
    }

    public void becomeDown(PortFsmState from, PortFsmState to, PortFsmEvent event) {
        dashboardLogger.onPortDown(endpoint);
        sendPortHistory(event, carrier);
    }

    private void sendPortHistory(PortFsmEvent event, IPortCarrier carrier) {
        carrier.sendPortStateChangedHistory(endpoint, getPortHistoryEvent(event), Instant.now());
    }

    private PortHistoryEvent getPortHistoryEvent(PortFsmEvent event) {
        switch (event) {
            case PORT_UP:
                return PortHistoryEvent.PORT_UP;
            case PORT_DOWN:
                return PortHistoryEvent.PORT_DOWN;
            default:
                throw new UnsupportedOperationException(
                        format("There is no matched port history event for port event %s", event));
        }
    }

    // -- service data types --

    public static class PortReportFsmFactory {
        private final NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder;
        private final IPortCarrier carrier;
        private final StateMachineBuilder<PortReportFsm, PortFsmState, PortFsmEvent, Void> builder;

        PortReportFsmFactory(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder, IPortCarrier carrier) {
            this.dashboardLoggerBuilder = dashboardLoggerBuilder;
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(
                    PortReportFsm.class, PortFsmState.class, PortFsmEvent.class, Void.class,
                    // extra parameters
                    NetworkTopologyDashboardLogger.Builder.class, Endpoint.class, IPortCarrier.class);

            // INIT
            builder.transition()
                    .from(PortFsm.PortFsmState.INIT).to(PortFsm.PortFsmState.UP).on(PortFsm.PortFsmEvent.PORT_UP);
            builder.transition()
                    .from(PortFsm.PortFsmState.INIT).to(PortFsm.PortFsmState.DOWN).on(PortFsm.PortFsmEvent.PORT_DOWN);

            // UP
            builder.transition()
                    .from(PortFsm.PortFsmState.UP).to(PortFsm.PortFsmState.DOWN).on(PortFsm.PortFsmEvent.PORT_DOWN);
            builder.onEntry(PortFsm.PortFsmState.UP)
                    .callMethod("becomeUp");
            // DOWN
            builder.transition()
                    .from(PortFsm.PortFsmState.DOWN).to(PortFsm.PortFsmState.UP).on(PortFsm.PortFsmEvent.PORT_UP);
            builder.onEntry(PortFsm.PortFsmState.DOWN)
                    .callMethod("becomeDown");
        }

        public PortReportFsm produce(Endpoint endpoint) {
            return builder.newStateMachine(PortFsm.PortFsmState.INIT, dashboardLoggerBuilder, endpoint, carrier);
        }
    }
}
