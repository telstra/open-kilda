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

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;

/**
 * Convert {@link FlowPath} to {@link PathInfoData} and back.
 */
@Mapper
public abstract class FlowPathMapper {

    public static final FlowPathMapper INSTANCE = Mappers.getMapper(FlowPathMapper.class);

    /**
     * Convert {@link FlowPath} to {@link PathInfoData}.
     */
    public PathInfoData map(FlowPath path) {
        PathInfoData result = new PathInfoData();
        result.setLatency(path.getLatency());

        int seqId = 0;
        List<PathNode> nodes = new ArrayList<>();
        for (PathSegment pathSegment : path.getSegments()) {
            nodes.add(new PathNode(pathSegment.getSrcSwitch().getSwitchId(), pathSegment.getSrcPort(),
                    seqId++, pathSegment.getLatency()));
            nodes.add(new PathNode(pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort(),
                    seqId++));
        }

        result.setPath(nodes);

        return result;
    }

    /**
     * Convert {@link FlowPath} to {@link PathNodePayload}.
     */
    public List<PathNodePayload> mapToPathNodes(FlowPath flowPath) {
        List<PathNodePayload> resultList = new ArrayList<>();

        Flow flow = flowPath.getFlow();
        int srcPort = flow.isForward(flowPath) ? flow.getSrcPort() : flow.getDestPort();
        int destPort = flow.isForward(flowPath) ? flow.getSrcPort() : flow.getDestPort();

        if (flowPath.getSegments().isEmpty()) {
            resultList.add(
                    new PathNodePayload(flowPath.getSrcSwitch().getSwitchId(), srcPort, destPort));
        } else {
            List<PathSegment> pathSegments = flowPath.getSegments();

            resultList.add(new PathNodePayload(flowPath.getSrcSwitch().getSwitchId(), srcPort,
                    pathSegments.get(0).getSrcPort()));

            for (int i = 1; i < pathSegments.size(); i++) {
                PathSegment inputNode = pathSegments.get(i - 1);
                PathSegment outputNode = pathSegments.get(i);

                resultList.add(new PathNodePayload(inputNode.getDestSwitch().getSwitchId(), inputNode.getDestPort(),
                        outputNode.getSrcPort()));
            }

            resultList.add(new PathNodePayload(flowPath.getDestSwitch().getSwitchId(),
                    pathSegments.get(pathSegments.size() - 1).getDestPort(), destPort));
        }

        return resultList;
    }
}
