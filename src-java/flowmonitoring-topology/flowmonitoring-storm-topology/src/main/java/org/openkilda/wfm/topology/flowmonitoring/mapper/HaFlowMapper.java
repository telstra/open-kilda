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

package org.openkilda.wfm.topology.flowmonitoring.mapper;

import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathSegment;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowState;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper
public class HaFlowMapper {
    public static final HaFlowMapper INSTANCE = Mappers.getMapper(HaFlowMapper.class);

    /**
     * Convert HA-Sub-Flow path representation.
     */
    public FlowState toFlowState(HaSubFlow haSubFlow) {
        HaFlow haFlow = haSubFlow.getHaFlow();

        FlowState flowState = new FlowState();
        flowState.setForwardPath(toLinks(getPathSegments(haFlow.getForwardPath(), haSubFlow.getHaSubFlowId())));
        flowState.setReversePath(toLinks(getPathSegments(haFlow.getReversePath(), haSubFlow.getHaSubFlowId())));
        flowState.setHaFlowId(haFlow.getHaFlowId());
        return flowState;
    }

    private List<PathSegment> getPathSegments(HaFlowPath haFlowPath, String haSubFlowId) {
        return haFlowPath.getSubPaths().stream()
                .filter(f -> f.getHaSubFlowId().equals(haSubFlowId))
                .map(FlowPath::getSegments)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<Link> toLinks(List<PathSegment> pathSegments) {
        if (pathSegments == null) {
            return Collections.emptyList();
        }
        return pathSegments.stream()
                .map(this::toLink)
                .collect(Collectors.toList());
    }

    private Link toLink(PathSegment pathSegment) {
        return Link.builder()
                .destSwitchId(pathSegment.getDestSwitchId())
                .srcSwitchId(pathSegment.getSrcSwitchId())
                .srcPort(pathSegment.getSrcPort())
                .destPort(pathSegment.getDestPort())
                .build();
    }
}
