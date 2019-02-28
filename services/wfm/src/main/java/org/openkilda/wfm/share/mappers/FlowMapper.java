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
import java.util.UUID;

/**
 * Convert {@link UnidirectionalFlow} to {@link FlowDto} and back.
 */
@Mapper(uses = {FlowPathMapper.class})
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
    @Mapping(source = "flowPath", target = "flowPath")
    public abstract FlowDto map(UnidirectionalFlow flow);

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
    public UnidirectionalFlow map(FlowDto flow) {
        return map(flow, true);
    }

    /**
     * Convert {@link FlowPairDto} to {@link FlowPair}.
     */
    public FlowPair map(FlowPairDto<FlowDto, FlowDto> flowPair) {
        if (flowPair == null) {
            return null;
        }

        UnidirectionalFlow forward = map(flowPair.getLeft(), true);
        UnidirectionalFlow reverse = map(flowPair.getRight(), false);
        Flow resultFlow = forward.getFlowEntity();
        resultFlow.setReversePath(reverse.getFlowPath());
        return new FlowPair(resultFlow, forward.getTransitVlanEntity(), reverse.getTransitVlanEntity());
    }

    private UnidirectionalFlow map(FlowDto flow, boolean forward) {
        Switch srcSwitch = Switch.builder().switchId(flow.getSourceSwitch()).build();
        Switch destSwitch = Switch.builder().switchId(flow.getDestinationSwitch()).build();

        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .cookie(new Cookie(flow.getCookie()))
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .flowId(flow.getFlowId())
                .pathId(new PathId(UUID.randomUUID().toString()))
                .meterId(flow.getMeterId() != null ? new MeterId(flow.getMeterId()) : null)
                .segments(Collections.emptyList())
                .build();

        Flow resultFlow = Flow.builder()
                .flowId(flow.getFlowId())
                .srcSwitch(forward ? srcSwitch : destSwitch)
                .destSwitch(forward ? destSwitch : srcSwitch)
                .srcPort(forward ? flow.getSourcePort() : flow.getDestinationPort())
                .destPort(forward ? flow.getDestinationPort() : flow.getSourcePort())
                .srcVlan(forward ? flow.getSourceVlan() : flow.getDestinationVlan())
                .destVlan(forward ? flow.getDestinationVlan() : flow.getSourceVlan())
                .status(map(flow.getState()))
                .description(flow.getDescription())
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .periodicPings(flow.isPeriodicPings())
                .forwardPath(forward ? flowPath : null)
                .reversePath(forward ? null : flowPath)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        TransitVlan transitVlan = TransitVlan.builder()
                .flowId(flow.getFlowId())
                .pathId(flowPath.getPathId())
                .vlan(flow.getTransitVlan())
                .build();

        return new UnidirectionalFlow(resultFlow, flowPath, transitVlan, forward);
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
