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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.messaging.model.FlowDto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class GetFlowsPerPortForSwitchResponseTest {
    @Test
    public void splitAndUniteLoop() {
        GetFlowsPerPortForSwitchResponse originalResponse = GetFlowsPerPortForSwitchResponse.builder()
                .flowsByPort(1, buildRandomFlowDtos(1, 10))
                .flowsByPort(2, buildRandomFlowDtos(2, 20))
                .flowsByPort(3, buildRandomFlowDtos(3, 7))
                .flowsByPort(4, buildRandomFlowDtos(4, 4))
                .flowsByPort(5, buildRandomFlowDtos(5, 12))
                .build();
        List<GetFlowsPerPortForSwitchResponse> splittedList = originalResponse.split(10);
        assertEquals(6, splittedList.size());
        for (int i = 0; i < splittedList.size() - 1; i++) {
            assertEquals(10, getSize(splittedList.get(i)));
        }
        assertEquals(3, getSize(splittedList.get(splittedList.size() - 1)));
        assertEquals(originalResponse, GetFlowsPerPortForSwitchResponse.unite(splittedList));
    }

    @Test
    public void emptyResponse() {
        GetFlowsPerPortForSwitchResponse originalResponse = GetFlowsPerPortForSwitchResponse.builder()
                .flowsByPorts(new HashMap<>())
                .build();
        List<GetFlowsPerPortForSwitchResponse> splittedList = originalResponse.split(10);
        assertEquals(1, splittedList.size());
        assertEquals(0, splittedList.get(0).getFlowsByPorts().size());
        assertEquals(originalResponse, GetFlowsPerPortForSwitchResponse.unite(splittedList));
    }

    private int getSize(GetFlowsPerPortForSwitchResponse response) {
        int size = 0;
        for (Collection<FlowDto> flowDtos : response.getFlowsByPorts().values()) {
            size += flowDtos.size();
        }
        return size;
    }

    private List<FlowDto> buildRandomFlowDtos(int portNumber, int size) {
        List<FlowDto> flows = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            flows.add(FlowDto.builder()
                    .flowId(String.format("flow_%s_%s", portNumber, size))
                    .bandwidth(portNumber)
                    .build());
        }
        return flows;
    }
}
