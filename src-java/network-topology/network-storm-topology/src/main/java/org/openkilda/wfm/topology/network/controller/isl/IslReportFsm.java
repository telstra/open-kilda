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

package org.openkilda.wfm.topology.network.controller.isl;

import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

@Slf4j
public class IslReportFsm extends
        AbstractStateMachine<IslReportFsm, IslReportFsm.State, IslReportFsm.Event, Object> {
    private final NetworkTopologyDashboardLogger dashboardLogger;

    private final IslReference islReference;

    public static IslReportFsmFactory factory(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder) {
        return new IslReportFsmFactory(dashboardLoggerBuilder);
    }

    public IslReportFsm(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder, IslReference islReference) {
        this.islReference = islReference;
        this.dashboardLogger = dashboardLoggerBuilder.build(log);
    }

    public void upEnter(State from, State to, Event event, Object context) {
        dashboardLogger.onIslUp(islReference);
    }

    public void downEnter(State from, State to, Event event, Object context) {
        dashboardLogger.onIslDown(islReference);
    }

    public void movedEnter(State from, State to, Event event, Object context) {
        dashboardLogger.onIslMoved(islReference);
    }

    public static class IslReportFsmFactory {
        private final NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder;
        private final StateMachineBuilder<IslReportFsm, State, Event, Object> builder;

        IslReportFsmFactory(NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder) {
            this.dashboardLoggerBuilder = dashboardLoggerBuilder;

            builder = StateMachineBuilderFactory.create(
                    IslReportFsm.class, State.class, Event.class, Object.class,
                    // extra parameters
                    NetworkTopologyDashboardLogger.Builder.class, IslReference.class);

            // UNDEFINED
            builder.transition()
                    .from(State.UNDEFINED).to(State.UP).on(Event.BECOME_UP);
            builder.transition()
                    .from(State.UNDEFINED).to(State.DOWN).on(Event.BECOME_DOWN);
            builder.transition()
                    .from(State.UNDEFINED).to(State.MOVED).on(Event.BECOME_MOVED);

            // UP
            builder.transition()
                    .from(State.UP).to(State.DOWN).on(Event.BECOME_DOWN);
            builder.transition()
                    .from(State.UP).to(State.MOVED).on(Event.BECOME_MOVED);
            builder.onEntry(State.UP)
                    .callMethod("upEnter");

            // DOWN
            builder.transition()
                    .from(State.DOWN).to(State.UP).on(Event.BECOME_UP);
            builder.transition()
                    .from(State.DOWN).to(State.MOVED).on(Event.BECOME_MOVED);
            builder.onEntry(State.DOWN)
                    .callMethod("downEnter");

            // MOVED
            builder.transition()
                    .from(State.MOVED).to(State.UP).on(Event.BECOME_UP);
            builder.transition()
                    .from(State.MOVED).to(State.DOWN).on(Event.BECOME_DOWN);
            builder.onEntry(State.MOVED)
                    .callMethod("movedEnter");
        }

        /**
         * Create and properly initialize new {@link IslReportFsm}.
         */
        public IslReportFsm produce(IslReference reference) {
            IslReportFsm fsm = builder.newStateMachine(State.UNDEFINED, dashboardLoggerBuilder, reference);
            fsm.start();
            return fsm;
        }
    }

    public enum State {
        UNDEFINED, UP, DOWN, MOVED
    }

    public enum Event {
        BECOME_UP, BECOME_DOWN, BECOME_MOVED
    }
}
