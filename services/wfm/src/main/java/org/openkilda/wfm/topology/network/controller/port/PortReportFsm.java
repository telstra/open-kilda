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

import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.annotation.ContextInsensitive;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

@Slf4j
@ContextInsensitive
public class PortReportFsm
        extends AbstractStateMachine<PortReportFsm, PortFsm.PortFsmState, PortFsm.PortFsmEvent, Void> {

    private final NetworkTopologyDashboardLogger dashboardLogger;

    private final Endpoint endpoint;

    private static StateMachineBuilder<PortReportFsm, PortFsm.PortFsmState, PortFsm.PortFsmEvent, Void> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                PortReportFsm.class, PortFsm.PortFsmState.class, PortFsm.PortFsmEvent.class, Void.class,
                // extra parameters
                NetworkTopologyDashboardLogger.Builder.class, Endpoint.class);

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

    public static PortReportFsm create(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder,
                                       Endpoint endpoint) {
        return builder.newStateMachine(PortFsm.PortFsmState.INIT, dashboardLoggerBuilder, endpoint);
    }

    public PortReportFsm(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuider, Endpoint endpoint) {
        this.dashboardLogger = dashboardLoggerBuider.build(log);
        this.endpoint = endpoint;
    }

    // -- FSM actions --

    public void becomeUp(PortFsm.PortFsmState from, PortFsm.PortFsmState to, PortFsm.PortFsmEvent event) {
        dashboardLogger.onUpdatePortStatus(endpoint, LinkStatus.UP);
    }

    public void becomeDown(PortFsm.PortFsmState from, PortFsm.PortFsmState to, PortFsm.PortFsmEvent event) {
        dashboardLogger.onUpdatePortStatus(endpoint, LinkStatus.DOWN);
    }
}
