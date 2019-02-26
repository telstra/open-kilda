/* Copyright 2018 Telstra Open Source
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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.network.PathDto;
import org.openkilda.model.FlowPath;

import org.mapstruct.Mapper;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PathMapper {

    /**
     * Converts {@link FlowPath} to {@link PathDto}.
     */
    default PathDto mapToPath(FlowPath data) {
        List<PathNodePayload> nodes = new ArrayList<>();

        if (!data.getNodes().isEmpty()) {
            FlowPath.Node firstNode = data.getNodes().get(0);
            nodes.add(new PathNodePayload(firstNode.getSwitchId(), null, firstNode.getPortNo()));

            for (int i = 1; i < data.getNodes().size() - 1; i += 2) {
                FlowPath.Node inputNode = data.getNodes().get(i);
                FlowPath.Node outputNode = data.getNodes().get(i + 1);
                nodes.add(new PathNodePayload(inputNode.getSwitchId(), inputNode.getPortNo(), outputNode.getPortNo()));
            }

            FlowPath.Node lastNode = data.getNodes().get(data.getNodes().size() - 1);
            nodes.add(new PathNodePayload(lastNode.getSwitchId(), lastNode.getPortNo(), null));
        }

        return new PathDto(data.getMinAvailableBandwidth(), data.getLatency(), nodes);
    }
}
