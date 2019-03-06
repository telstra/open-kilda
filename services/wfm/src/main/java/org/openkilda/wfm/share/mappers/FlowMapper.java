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
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

/**
 * Convert {@link Flow} to {@link FlowDto} and back.
 */
@Mapper
public abstract class FlowMapper {

    public static final FlowMapper INSTANCE = Mappers.getMapper(FlowMapper.class);

    /**
     * Convert {@link Flow} to {@link FlowDto}.
     */
    @Mapping(source = "srcPort", target = "sourcePort")
    @Mapping(source = "srcVlan", target = "sourceVlan")
    @Mapping(source = "destPort", target = "destinationPort")
    @Mapping(source = "destVlan", target = "destinationVlan")
    @Mapping(target = "sourceSwitch", expression = "java(flow.getSrcSwitch().getSwitchId())")
    @Mapping(target = "destinationSwitch", expression = "java(flow.getDestSwitch().getSwitchId())")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "timeModify", target = "lastUpdated")
    @Mapping(source = "timeCreate", target = "createdTime")
    public abstract FlowDto map(Flow flow);

    /**
     * Builds a {@link Flow} with provided data from {@link FlowDto}.
     * <p/>
     * <strong>Be careful as it creates a dummy switch objects for srcSwitch and destSwitch properties
     * with only switchId filled.</strong>
     */
    @Mapping(source = "sourcePort", target = "srcPort")
    @Mapping(source = "sourceVlan", target = "srcVlan")
    @Mapping(source = "destinationPort", target = "destPort")
    @Mapping(source = "destinationVlan", target = "destVlan")
    @Mapping(target = "srcSwitch",
            expression = "java(org.openkilda.model.Switch.builder().switchId(flow.getSourceSwitch()).build())")
    @Mapping(target = "destSwitch",
            expression = "java(org.openkilda.model.Switch.builder().switchId(flow.getDestinationSwitch()).build())")
    @Mapping(source = "state", target = "status")
    @Mapping(source = "lastUpdated", target = "timeModify")
    @Mapping(source = "createdTime", target = "timeCreate")
    public abstract Flow map(FlowDto flow);

    /**
     * Convert {@link PathNode} to {@link FlowPath.Node}.
     */
    public abstract FlowPath.Node map(PathNode p);

    /**
     * Convert {@link FlowPath.Node } to {@link PathNode}.
     */
    public abstract PathNode map(FlowPath.Node p);

    /**
     * Convert {@link FlowPath} to {@link PathInfoData}.
     */
    @Mapping(source = "nodes", target = "path")
    public abstract PathInfoData map(FlowPath p);

    /**
     * Convert {@link PathInfoData} to {@link FlowPath}.
     */
    @Mapping(source = "path", target = "nodes")
    public abstract FlowPath map(PathInfoData p);


    /**
     * Convert {@link FlowPair} to {@link FlowPairDto}.
     */
    public FlowPairDto<FlowDto, FlowDto> map(FlowPair flowPair) {
        if (flowPair == null) {
            return null;
        }

        return new FlowPairDto<>(map(flowPair.getForward()), map(flowPair.getReverse()));
    }

    /**
     * Convert {@link FlowPairDto} to {@link FlowPair}.
     */
    public FlowPair map(FlowPairDto<FlowDto, FlowDto> flowPair) {
        if (flowPair == null) {
            return null;
        }

        return FlowPair.builder().forward(map(flowPair.getLeft())).reverse(map(flowPair.getRight())).build();
    }

    /**
     * Convert {@link String} to {@link Instant}.
     */
    public Instant map(String value) {
        if (value == null) {
            return null;
        }

        return Instant.parse(value);
    }

    /**
     * Convert {@link Instant} to {@link String}.
     */
    public String map(Instant value) {
        if (value == null) {
            return null;
        }

        return value.toString();
    }

    /**
     * Convert {@link FlowStatus} to {@link FlowState}.
     */
    public FlowState map(FlowStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case IN_PROGRESS:
                return FlowState.IN_PROGRESS;
            case UP:
                return FlowState.UP;
            case DOWN:
                return FlowState.DOWN;
            default:
                throw new IllegalArgumentException("Unsupported Flow status: " + status);
        }
    }

    /**
     * Convert {@link FlowState} to {@link FlowStatus}.
     */
    public FlowStatus map(FlowState status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case IN_PROGRESS:
                return FlowStatus.IN_PROGRESS;
            case UP:
                return FlowStatus.UP;
            case DOWN:
                return FlowStatus.DOWN;
            default:
                throw new IllegalArgumentException("Unsupported Flow status: " + status);
        }
    }
}
