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

package org.openkilda.wfm.topology.discovery.service;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.DecisionMakerFsm;
import org.openkilda.wfm.topology.discovery.controller.DecisionMakerFsmContext;
import org.openkilda.wfm.topology.discovery.controller.DecisionMakerFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.DecisionMakerFsmState;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoveryDecisionMakerService {

    private final Map<Endpoint, DecisionMakerFsm> controller = new HashMap<>();

    private final FsmExecutor<DecisionMakerFsm, DecisionMakerFsmState, DecisionMakerFsmEvent,
            DecisionMakerFsmContext> controllerExecutor
            = DecisionMakerFsm.makeExecutor();

    private final long failTimeout;
    private final long awaitTime;

    public DiscoveryDecisionMakerService(long failTimeout, long awaitTime) {
        this.failTimeout = failTimeout;
        this.awaitTime = awaitTime;
    }

    /**
     * .
     */
    public void discovered(IDecisionMakerCarrier carrier, Endpoint endpoint, IslInfoData discoveryEvent,
                           long currentTime) {
        log.debug("Discovery poll DISCOVERED notification on {}", endpoint);

        DecisionMakerFsm decisionMakerFsm = locateControllerCreateIfAbsent(endpoint);

        DecisionMakerFsmContext context = DecisionMakerFsmContext.builder()
                .currentTime(currentTime)
                .discoveryEvent(discoveryEvent)
                .output(carrier)
                .build();

        controllerExecutor.fire(decisionMakerFsm, DecisionMakerFsmEvent.DISCOVERY, context);

    }

    /**
     * Process "failed" event from {@link DiscoveryWatcherService}.
     */
    public void failed(IDecisionMakerCarrier carrier, Endpoint endpoint, long currentTime) {
        log.debug("Discovery poll FAIL notification on {}", endpoint);
        DecisionMakerFsm decisionMakerFsm = locateControllerCreateIfAbsent(endpoint);

        DecisionMakerFsmContext context = DecisionMakerFsmContext.builder()
                .currentTime(currentTime)
                .output(carrier)
                .build();

        controllerExecutor.fire(decisionMakerFsm, DecisionMakerFsmEvent.FAIL, context);
    }

    /**
     * Process "tick" event from {@link CoordinatorSpout}.
     */
    public void tick(IDecisionMakerCarrier carrier, long currentTime) {

        DecisionMakerFsmContext context = DecisionMakerFsmContext.builder()
                .currentTime(currentTime)
                .output(carrier)
                .build();
        for (DecisionMakerFsm fsm : controller.values()) {
            controllerExecutor.fire(fsm, DecisionMakerFsmEvent.TICK, context);
        }
    }

    private DecisionMakerFsm locateControllerCreateIfAbsent(Endpoint endpoint) {
        return controller.computeIfAbsent(endpoint, key -> DecisionMakerFsm.create(endpoint, failTimeout, awaitTime));
    }

    public void clear(IDecisionMakerCarrier carrier, Endpoint endpoint) {
        controller.remove(endpoint);
    }
}
