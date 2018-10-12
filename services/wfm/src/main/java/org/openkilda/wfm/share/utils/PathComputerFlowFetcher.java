/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.share.utils;

import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.share.mappers.FlowMapper;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PathComputerFlowFetcher {
    private static final Logger log = LoggerFactory.getLogger(PathComputerFlowFetcher.class);

    @Getter
    private final Collection<BidirectionalFlow> flows = new ArrayList<>();

    public PathComputerFlowFetcher(FlowRepository flowRepository) {
        for (FlowCollector collector : fetchFlows(flowRepository)) {
            flows.add(collector.make());
        }
    }

    private Collection<FlowCollector> fetchFlows(FlowRepository flowRepository) {
        Map<String, FlowCollector> flowPairsMap = new HashMap<>();
        for (org.openkilda.model.Flow flow : flowRepository.findAll()) {
            if (!flowPairsMap.containsKey(flow.getFlowId())) {
                flowPairsMap.put(flow.getFlowId(), new FlowCollector());
            }

            FlowCollector pair = flowPairsMap.get(flow.getFlowId());
            try {
                pair.add(FlowMapper.INSTANCE.map(flow));
            } catch (IllegalArgumentException e) {
                log.error("Invalid half-flow {}: {}", flow.getFlowId(), e.toString());
            }
        }

        return flowPairsMap.values();
    }
}
