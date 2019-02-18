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

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.info.network.Path;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPath.Node;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;

/**
 * Convert {@link FlowPath} to {@link Path}.
 */
@Mapper
public abstract class PathMapper {

    public static final PathMapper INSTANCE = Mappers.getMapper(PathMapper.class);

    /**
     * Convert {@link FlowPath} to {@link Path}.
     */
    public Path map(FlowPath flowPath) {
        if (flowPath == null || flowPath.getNodes().isEmpty()) {
            return new Path(0L, 0L, new ArrayList<>());
        }

        List<String> edges = new ArrayList<>();

        for (int i = 0; i < flowPath.getNodes().size(); i += 2) {
            Node srcNode = flowPath.getNodes().get(i);
            Node dstNode = flowPath.getNodes().get(i + 1);

            String edge = String.format("%s_%d ===> %s_%d",
                    srcNode.getSwitchId(), srcNode.getPortNo(), dstNode.getSwitchId(), dstNode.getPortNo());

            edges.add(edge);
        }
        return new Path(flowPath.getMinAvailableBandwidth(), flowPath.getLatency(), edges);
    }
}
