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

import static java.lang.String.format;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.NetworkEndpoint;
import org.openkilda.model.PathSegment;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.Iterator;
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
        if (path != null) {
            result.setLatency(path.getLatency());

            int seqId = 0;
            List<PathNode> nodes = new ArrayList<>();
            for (PathSegment pathSegment : path.getSegments()) {
                nodes.add(new PathNode(pathSegment.getSrcSwitchId(), pathSegment.getSrcPort(),
                        seqId++, pathSegment.getLatency()));
                nodes.add(new PathNode(pathSegment.getDestSwitchId(), pathSegment.getDestPort(),
                        seqId++));
            }

            result.setPath(nodes);
        }
        return result;
    }

    /**
     * Convert path's {@link PathSegment} to {@link PathInfoData}.
     */
    public PathInfoData map(List<PathSegment> pathSegments) {
        PathInfoData result = new PathInfoData();
        int seqId = 0;
        List<PathNode> nodes = new ArrayList<>();
        for (PathSegment pathSegment : pathSegments) {
            nodes.add(new PathNode(pathSegment.getSrcSwitchId(), pathSegment.getSrcPort(),
                    seqId++, pathSegment.getLatency()));
            nodes.add(new PathNode(pathSegment.getDestSwitchId(), pathSegment.getDestPort(),
                    seqId++));
        }

        result.setPath(nodes);
        return result;
    }

    /**
     * Convert {@link FlowPath} to {@link PathNodePayload}.
     */
    public List<PathNodePayload> mapToPathNodes(Flow flow, FlowPath flowPath) {
        FlowEndpoint ingress = FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint();
        FlowEndpoint egress = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint();

        List<PathSegment> pathSegments = flowPath.getSegments();
        return mapToPathNodes(ingress, pathSegments, egress);
    }

    /**
     * Convert {@link FlowPath} to {@link PathNodePayload}.
     */
    public List<PathNodePayload> mapToPathNodes(NetworkEndpoint ingress, List<PathSegment> pathSegments,
                                                NetworkEndpoint egress) {
        List<PathNodePayload> resultList = new ArrayList<>();

        Iterator<PathSegment> leftIter = pathSegments.iterator();
        Iterator<PathSegment> rightIter = pathSegments.iterator();
        if (!rightIter.hasNext()) {
            if (ingress != null && egress != null) {
                resultList.add(new PathNodePayload(
                        ingress.getSwitchId(), ingress.getPortNumber(), egress.getPortNumber()));
            }
        } else {
            PathSegment left;
            PathSegment right = rightIter.next();

            if (ingress != null) {
                if (!ingress.getSwitchId().equals(right.getSrcSwitchId())) {
                    throw new IllegalArgumentException(format("Provided ingress and path segments are not consistent: "
                            + "%s and %s have different switches", ingress.getSwitchId(), right));
                }
                resultList.add(new PathNodePayload(ingress.getSwitchId(), ingress.getPortNumber(), right.getSrcPort()));
            } else {
                // The case of the same dest port, but different src port.
                resultList.add(new PathNodePayload(right.getSrcSwitchId(), null, right.getSrcPort()));
            }
            while (rightIter.hasNext()) {
                left = leftIter.next();
                right = rightIter.next();
                if (!left.getDestSwitchId().equals(right.getSrcSwitchId())) {
                    throw new IllegalArgumentException(format("Provided path segments are not consistent: "
                            + "%s and %s have different switches", left, right));
                }
                resultList.add(new PathNodePayload(
                        left.getDestSwitchId(), left.getDestPort(), right.getSrcPort()));
            }
            if (egress != null) {
                if (!egress.getSwitchId().equals(right.getDestSwitchId())) {
                    throw new IllegalArgumentException(format("Provided egress and path segments are not consistent: "
                            + "%s and %s have different switches", egress.getSwitchId(), right));
                }
                resultList.add(new PathNodePayload(egress.getSwitchId(), right.getDestPort(), egress.getPortNumber()));
            } else {
                // The case of the same src port, but different dest port.
                resultList.add(new PathNodePayload(right.getDestSwitchId(), right.getDestPort(), null));
            }
        }

        return resultList;
    }

    /**
     * Convert {@link FlowPath} to {@link PathNodePayload}.
     */
    public List<PathNodePayload> mapToPathNodes(FlowPath flowPath, int inPort, int outPort) {
        List<PathNodePayload> resultList = new ArrayList<>();

        if (flowPath.getSegments().isEmpty()) {
            resultList.add(
                    new PathNodePayload(flowPath.getSrcSwitchId(), inPort, outPort));
        } else {
            List<PathSegment> pathSegments = flowPath.getSegments();

            resultList.add(new PathNodePayload(flowPath.getSrcSwitchId(), inPort,
                    pathSegments.get(0).getSrcPort()));

            for (int i = 1; i < pathSegments.size(); i++) {
                PathSegment inputNode = pathSegments.get(i - 1);
                PathSegment outputNode = pathSegments.get(i);

                resultList.add(new PathNodePayload(inputNode.getDestSwitchId(), inputNode.getDestPort(),
                        outputNode.getSrcPort()));
            }

            resultList.add(new PathNodePayload(flowPath.getDestSwitchId(),
                    pathSegments.get(pathSegments.size() - 1).getDestPort(), outPort));
        }

        return resultList;
    }
}
