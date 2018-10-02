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
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.FlowPair;
import org.openkilda.model.Node;
import org.openkilda.model.Path;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

/**
 * Convert {@link org.openkilda.model.Flow} to {@link Flow} and back.
 */
@SuppressWarnings("squid:S1214")
@Mapper(uses = SwitchIdMapper.class)
public interface FlowMapper {

    FlowMapper INSTANCE = Mappers.getMapper(FlowMapper.class);

    /**
     * Convert {@link org.openkilda.model.Flow} to {@link org.openkilda.messaging.model.Flow}.
     */
    @Mapping(source = "srcPort", target = "sourcePort")
    @Mapping(source = "srcVlan", target = "sourceVlan")
    @Mapping(source = "destPort", target = "destinationPort")
    @Mapping(source = "destVlan", target = "destinationVlan")
    @Mapping(source = "srcSwitchId", target = "sourceSwitch")
    @Mapping(source = "destSwitchId", target = "destinationSwitch")
    @Mapping(source = "status", target = "state")
    Flow map(org.openkilda.model.Flow flow);

    /**
     * Convert {@link org.openkilda.messaging.model.Flow} to {@link org.openkilda.model.Flow}.
     */
    @Mapping(source = "sourcePort", target = "srcPort")
    @Mapping(source = "sourceVlan", target = "srcVlan")
    @Mapping(source = "destinationPort", target = "destPort")
    @Mapping(source = "destinationVlan", target = "destVlan")
    @Mapping(source = "sourceSwitch", target = "srcSwitchId")
    @Mapping(source = "destinationSwitch", target = "destSwitchId")
    @Mapping(source = "state", target = "status")
    org.openkilda.model.Flow map(Flow flow);

    /**
     * Convert {@link org.openkilda.messaging.info.event.PathNode} to {@link org.openkilda.model.Node}.
     */
    Node map(PathNode p);

    /**
     * Convert {@link org.openkilda.model.Node} to {@link org.openkilda.messaging.info.event.PathNode}.
     */
    PathNode map(Node p);

    /**
     * Convert {@link org.openkilda.model.Path} to {@link org.openkilda.messaging.info.event.PathInfoData}.
     */
    @Mapping(source = "nodes", target = "path")
    PathInfoData map(Path p);

    /**
     * Convert {@link org.openkilda.messaging.info.event.PathInfoData} to {@link org.openkilda.model.Path}.
     */
    @Mapping(source = "path", target = "nodes")
    Path map(PathInfoData p);


    /**
     * Convert {@link org.openkilda.model.FlowPair} to {@link org.openkilda.messaging.model.FlowPair}.
     */
    default org.openkilda.messaging.model.FlowPair map(FlowPair flowPair) {
        return new org.openkilda.messaging.model.FlowPair(map(flowPair.getForward()),
                map(flowPair.getReverse()));
    }

    /**
     * Convert {@link org.openkilda.messaging.model.FlowPair} to {@link org.openkilda.model.FlowPair}.
     */
    default FlowPair map(org.openkilda.messaging.model.FlowPair<org.openkilda.messaging.model.Flow,
            org.openkilda.messaging.model.Flow> flowPair) {

        return FlowPair.builder().forward(map(flowPair.getLeft()))
                .reverse(map(flowPair.getRight())).build();
    }

    /**
     * Convert {@link String} to {@link Instant}.
     */
    default Instant map(String value) {
        if (value == null) {
            return null;
        }

        return Instant.parse(value);
    }

    /**
     * Convert {@link Instant} to {@link String}.
     */
    default String map(Instant value) {
        if (value == null) {
            return null;
        }

        return value.toString();
    }

    /**
     * Convert {@link org.openkilda.model.FlowStatus} to {@link FlowState}.
     */
    default FlowState map(org.openkilda.model.FlowStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case IN_PROGRESS:
                return FlowState.IN_PROGRESS;
            case UP:
                return FlowState.UP;
            case DOWN:
                return FlowState.DOWN;
        }
    }

    /**
     * Convert {@link FlowState} to {@link org.openkilda.model.FlowStatus}.
     */
    default org.openkilda.model.FlowStatus map(FlowState status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case IN_PROGRESS:
                return org.openkilda.model.FlowStatus.IN_PROGRESS;
            case UP:
                return org.openkilda.model.FlowStatus.UP;
            case DOWN:
                return org.openkilda.model.FlowStatus.DOWN;
        }
    }
}
