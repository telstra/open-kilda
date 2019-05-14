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

import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.UnidirectionalFlow;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Convert {@link UnidirectionalFlow} to {@link FlowDto} and back.
 */
@Mapper(uses = {FlowPathMapper.class})
public abstract class FlowMapper {

    public static final FlowMapper INSTANCE = Mappers.getMapper(FlowMapper.class);

    /**
     * Convert {@link UnidirectionalFlow} to {@link FlowDto}.
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
    @Mapping(source = "pinned", target = "pinned")
    public abstract FlowDto map(UnidirectionalFlow flow);

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
     * Convert {@link FlowPair} to {@link FlowPairDto}.
     */
    public FlowPairDto<FlowDto, FlowDto> map(FlowPair flowPair) {
        if (flowPair == null) {
            return null;
        }

        return new FlowPairDto<>(
                map(flowPair.getForward()),
                map(flowPair.getReverse()));
    }

    /**
     * Builds a {@link UnidirectionalFlow} with provided data from {@link FlowDto}.
     * <p/>
     * <strong>Be careful as it creates a dummy switch objects for srcSwitch and destSwitch properties
     * with only switchId filled.</strong>
     */
    public UnidirectionalFlow map(FlowDto flowDto) {
        Flow flow = buildFlow(flowDto);

        FlowPath flowPath = buildPath(flow, flowDto);
        flow.setForwardPath(flowPath);

        TransitVlan transitVlan = TransitVlan.builder()
                .flowId(flow.getFlowId())
                .pathId(flowPath.getPathId())
                .vlan(flowDto.getTransitVlan())
                .build();

        return new UnidirectionalFlow(flowPath, transitVlan, true);
    }

    /**
     * Convert {@link FlowPairDto} to {@link FlowPair}.
     */
    public FlowPair map(FlowPairDto<FlowDto, FlowDto> flowPair) {
        if (flowPair == null) {
            return null;
        }

        Flow flow = buildFlow(flowPair.getLeft());

        FlowPath forwardPath = buildPath(flow, flowPair.getLeft());
        FlowPath reversePath = buildPath(flow, flowPair.getRight());

        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);

        TransitVlan forwardTransitVlan = TransitVlan.builder()
                .flowId(flow.getFlowId())
                .pathId(forwardPath.getPathId())
                .vlan(flowPair.getLeft().getTransitVlan())
                .build();

        TransitVlan reverseTransitVlan = TransitVlan.builder()
                .flowId(flow.getFlowId())
                .pathId(reversePath.getPathId())
                .vlan(flowPair.getRight().getTransitVlan())
                .build();

        return new FlowPair(flow, forwardTransitVlan, reverseTransitVlan);
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

    public long map(Cookie cookie) {
        return cookie.getValue();
    }

    public Long map(MeterId meterId) {
        return Optional.ofNullable(meterId).map(MeterId::getValue).orElse(null);
    }
    
    /**
     * Convert {@link org.openkilda.messaging.payload.flow.FlowEncapsulationType} to {@link FlowEncapsulationType}.
     */
    public FlowEncapsulationType map(org.openkilda.messaging.payload.flow.FlowEncapsulationType encapsulationType) {
        return encapsulationType != null ? FlowEncapsulationType.valueOf(encapsulationType.name()) : null;
    }

    /**
     * Convert {@link FlowEncapsulationType} to {@link org.openkilda.messaging.payload.flow.FlowEncapsulationType}.
     */
    public org.openkilda.messaging.payload.flow.FlowEncapsulationType map(FlowEncapsulationType encapsulationType) {
        return encapsulationType != null ? org.openkilda.messaging.payload.flow.FlowEncapsulationType.valueOf(
                encapsulationType.name()) : null;
    }

    private FlowPath buildPath(Flow flow, FlowDto flowDto) {
        Switch srcSwitch = Switch.builder().switchId(flowDto.getSourceSwitch()).build();
        Switch destSwitch = Switch.builder().switchId(flowDto.getDestinationSwitch()).build();

        return FlowPath.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .cookie(new Cookie(flowDto.getCookie()))
                .bandwidth(flowDto.getBandwidth())
                .ignoreBandwidth(flowDto.isIgnoreBandwidth())
                .flow(flow)
                .pathId(new PathId(UUID.randomUUID().toString()))
                .meterId(flowDto.getMeterId() != null ? new MeterId(flowDto.getMeterId()) : null)
                .segments(Collections.emptyList())
                .timeCreate(map(flowDto.getCreatedTime()))
                .timeModify(map(flowDto.getLastUpdated()))
                .build();
    }

    private Flow buildFlow(FlowDto flow) {
        Switch srcSwitch = Switch.builder().switchId(flow.getSourceSwitch()).build();
        Switch destSwitch = Switch.builder().switchId(flow.getDestinationSwitch()).build();

        return Flow.builder()
                .flowId(flow.getFlowId())
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .srcPort(flow.getSourcePort())
                .destPort(flow.getDestinationPort())
                .srcVlan(flow.getSourceVlan())
                .destVlan(flow.getDestinationVlan())
                .status(map(flow.getState()))
                .description(flow.getDescription())
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .periodicPings(flow.isPeriodicPings())
                .allocateProtectedPath(flow.isAllocateProtectedPath())
                .encapsulationType(map(flow.getEncapsulationType()))
                .maxLatency(flow.getMaxLatency())
                .priority(flow.getPriority())
                .timeCreate(map(flow.getCreatedTime()))
                .timeModify(map(flow.getLastUpdated()))
                .pinned(flow.isPinned())
                .build();
    }
}
