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

package org.openkilda.messaging.nbtopology.response;


import static org.openkilda.messaging.Utils.getNonNullEntries;

import org.openkilda.messaging.Chunkable;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.split.SplitIterator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Represents a mapping port->[flows] for a specific switch.
 */
@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class GetFlowsPerPortForSwitchResponse extends InfoData implements Chunkable<GetFlowsPerPortForSwitchResponse> {
    @Singular
    Map<Integer, Collection<FlowDto>> flowsByPorts;

    @Override
    public List<GetFlowsPerPortForSwitchResponse> split(int chunkSize) {
        List<GetFlowsPerPortForSwitchResponse> result = new ArrayList<>();
        SplitIterator<GetFlowsPerPortForSwitchResponse, GetFlowsPerPortForSwitchResponseBuilder> iterator
                = new SplitIterator<>(chunkSize, chunkSize, GetFlowsPerPortForSwitchResponseBuilder::build,
                GetFlowsPerPortForSwitchResponse::builder);

        if (flowsByPorts != null) {
            for (Integer port : flowsByPorts.keySet()) {
                for (List<FlowDto> flows : Utils.split(
                        new ArrayList<>(flowsByPorts.get(port)), iterator.getRemainingChunkSize(), chunkSize)) {
                    iterator.getCurrentBuilder().flowsByPort(port, flows);
                    iterator.next(flows.size()).ifPresent(result::add);
                }
            }
        }

        if (iterator.isAddCurrent()) {
            result.add(iterator.getCurrentBuilder().build());
        }
        return result;
    }

    /**
     * Unites several entities into one.
     */
    public static GetFlowsPerPortForSwitchResponse unite(List<GetFlowsPerPortForSwitchResponse> dataList) {
        if (dataList == null) {
            return null;
        }
        List<GetFlowsPerPortForSwitchResponse> nonNullData = getNonNullEntries(dataList);
        if (nonNullData.isEmpty()) {
            return null;
        }

        Map<Integer, Collection<FlowDto>> flowsByPorts = new TreeMap<>();

        for (GetFlowsPerPortForSwitchResponse response : dataList) {
            if (response.flowsByPorts != null) {
                for (Integer port : response.flowsByPorts.keySet()) {
                    flowsByPorts.putIfAbsent(port, new ArrayList<>());
                    flowsByPorts.get(port).addAll(response.flowsByPorts.get(port));
                }
            }
        }

        return new GetFlowsPerPortForSwitchResponse(flowsByPorts);
    }
}
