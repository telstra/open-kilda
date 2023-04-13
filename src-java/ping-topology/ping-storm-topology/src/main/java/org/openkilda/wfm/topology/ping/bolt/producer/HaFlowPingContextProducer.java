/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.bolt.producer;

import org.openkilda.messaging.model.FlowDirection;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces ping requests for forward and reverse flows.
 */
public class HaFlowPingContextProducer implements PingContextProducer {
    protected transient Logger log = LoggerFactory.getLogger(getClass());

    /**
     * This method produces a list of PingContexts based on the given PingContext.
     * The produced PingContexts are created by iterating through the forward and reverse paths of the given HaFlow,
     * creating Ping objects for each sub-path in the HaFlowPath, and using these Ping objects to create new
     * PingContexts with the HaSubFlowId and FlowDirection set accordingly.
     *
     * @param pingContext the PingContext to produce PingContexts from
     * @return a list of PingContexts produced from the given PingContext
     */
    @Override
    public List<PingContext> produce(final PingContext pingContext) {
        final List<PingContext> pingContexts = new ArrayList<>();

        HaFlow haFlow = pingContext.getHaFlow();

        pingContexts.addAll(createPingContextsFromHaFlowPath(pingContext, FlowDirection.FORWARD, haFlow,
                haFlow.getForwardPath()));
        pingContexts.addAll(createPingContextsFromHaFlowPath(pingContext, FlowDirection.REVERSE, haFlow,
                haFlow.getReversePath()));

        return pingContexts;
    }

    private List<PingContext> createPingContextsFromHaFlowPath(final PingContext pingContext,
                                                               final FlowDirection direction,
                                                               final HaFlow haFlow,
                                                               final HaFlowPath haFlowPath) {
        final List<PingContext> pingContexts = new ArrayList<>();

        for (FlowPath subFlowPath : haFlowPath.getSubPaths()) {
            HaSubFlow haSubFlow = subFlowPath.getHaSubFlow();
            if (haSubFlow.getEndpointSwitchId().equals(haFlow.getSharedSwitchId())) {
                log.warn("Skip ping for ha sub-flow {} because it is one-switch flow", haSubFlow.getHaSubFlowId());
                continue;
            }
            NetworkEndpoint networkEndpoint1 =
                    new NetworkEndpoint(haFlow.getSharedSwitchId(), haFlow.getSharedPort());
            NetworkEndpoint networkEndpoint2 =
                    new NetworkEndpoint(haSubFlow.getEndpointSwitchId(), haSubFlow.getEndpointPort());

            NetworkEndpoint src = direction == FlowDirection.FORWARD ? networkEndpoint1 : networkEndpoint2;
            NetworkEndpoint dst = direction == FlowDirection.FORWARD ? networkEndpoint2 : networkEndpoint1;

            pingContexts.add(pingContext.toBuilder()
                    .ping(new Ping(src, dst, pingContext.getTransitEncapsulation(),
                            ProducerUtils.getFirstIslPort(subFlowPath)))
                    .haSubFlowId(haSubFlow.getHaSubFlowId())
                    .direction(direction)
                    .build());
        }
        return pingContexts;
    }
}
