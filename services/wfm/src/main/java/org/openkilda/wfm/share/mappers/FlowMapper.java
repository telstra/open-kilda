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
import org.openkilda.messaging.model.SwapFlowDto;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowStatusDetails;
import org.openkilda.model.Cookie;
import org.openkilda.model.EncapsulationId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.model.Vxlan;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Convert {@link UnidirectionalFlow} to {@link FlowDto} and back.
 */
@Mapper(uses = {FlowPathMapper.class, DetectConnectedDevicesMapper.class}, imports = {FlowStatusDetails.class})
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
    @Mapping(target = "flowStatusDetails",
            expression = "java(flow.getFlow().isAllocateProtectedPath() ? "
                    + "new FlowStatusDetails(flow.getFlow().getMainFlowPrioritizedPathsStatus(), "
                    + "flow.getFlow().getProtectedFlowPrioritizedPathsStatus()) : null)")
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
    @Mapping(target = "flowStatusDetails",
            expression = "java(flow.isAllocateProtectedPath() ? "
                    + "new FlowStatusDetails(flow.getMainFlowPrioritizedPathsStatus(), "
                    + "flow.getProtectedFlowPrioritizedPathsStatus()) : null)")
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
     * If encapsulation type and/or path computation strategy is not provided then values from KildaConfiguration
     * will be used.
     */
    public UnidirectionalFlow map(FlowDto flowDto, Supplier<KildaConfiguration> kildaConfiguration) {
        Flow flow = buildFlow(flowDto, kildaConfiguration);

        FlowPath flowPath = buildPath(flow, flowDto);
        flow.setForwardPath(flowPath);
        EncapsulationId encapsulationId = convertToEncapsulationId(flow, flowPath, flowDto);

        return new UnidirectionalFlow(flowPath, encapsulationId, true);
    }


    /**
     * Convert {@link FlowPairDto} to {@link FlowPair}.
     * If encapsulation type and/or path computation strategy is not provided then values from KildaConfiguration
     * will be used.
     */
    public FlowPair map(FlowPairDto<FlowDto, FlowDto> flowPair, Supplier<KildaConfiguration> kildaConfiguration) {
        if (flowPair == null) {
            return null;
        }

        Flow flow = buildFlow(flowPair.getLeft(), kildaConfiguration);

        FlowPath forwardPath = buildPath(flow, flowPair.getLeft());
        FlowPath reversePath = buildPath(flow, flowPair.getRight());

        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);
        EncapsulationId forwardEncapsulationId = convertToEncapsulationId(flow, forwardPath, flowPair.getLeft());

        EncapsulationId reverseEncapsulationId = convertToEncapsulationId(flow, reversePath, flowPair.getRight());

        return new FlowPair(flow, forwardEncapsulationId, reverseEncapsulationId);
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
            case DEGRADED:
                return FlowState.DEGRADED;
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
            case DEGRADED:
                return FlowStatus.DEGRADED;
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
     * Convert {@link FlowEncapsulationType} to {@link org.openkilda.messaging.payload.flow.FlowEncapsulationType}.
     */
    public org.openkilda.messaging.payload.flow.FlowEncapsulationType map(FlowEncapsulationType encapsulationType) {
        return encapsulationType != null ? org.openkilda.messaging.payload.flow.FlowEncapsulationType.valueOf(
                encapsulationType.name()) : null;
    }

    /**
     * Convert {@link PathComputationStrategy} to {@link org.openkilda.messaging.payload.flow.PathComputationStrategy}.
     */
    public org.openkilda.messaging.payload.flow.PathComputationStrategy map(
            PathComputationStrategy pathComputationStrategy) {
        return pathComputationStrategy != null ? org.openkilda.messaging.payload.flow.PathComputationStrategy.valueOf(
                pathComputationStrategy.name()) : null;
    }

    private EncapsulationId convertToEncapsulationId(Flow flow, FlowPath flowPath, FlowDto flowDto) {
        FlowEncapsulationType flowEncapsulationType = flow.getEncapsulationType();
        EncapsulationId encapsulationId = null;
        if (FlowEncapsulationType.TRANSIT_VLAN.equals(flowEncapsulationType)) {

            encapsulationId = TransitVlan.builder()
                    .flowId(flow.getFlowId())
                    .pathId(flowPath.getPathId())
                    .vlan(flowDto.getTransitEncapsulationId())
                    .build();
        } else if (FlowEncapsulationType.VXLAN.equals(flowEncapsulationType)) {
            encapsulationId = Vxlan.builder()
                    .flowId(flow.getFlowId())
                    .pathId(flowPath.getPathId())
                    .vni(flowDto.getTransitEncapsulationId())
                    .build();
        }
        return encapsulationId;
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

    private Flow buildFlow(FlowDto flow, Supplier<KildaConfiguration> kildaConfiguration) {
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
                .encapsulationType(Optional.ofNullable(flow.getEncapsulationType())
                    .map(encapsulationType -> FlowEncapsulationType.valueOf(encapsulationType.name()))
                    .orElse(kildaConfiguration.get().getFlowEncapsulationType()))
                .pathComputationStrategy(Optional.ofNullable(flow.getPathComputationStrategy())
                        .map(pathComputationStrategy -> PathComputationStrategy.valueOf(pathComputationStrategy.name()))
                        .orElse(kildaConfiguration.get().getPathComputationStrategy()))
                .maxLatency(flow.getMaxLatency())
                .priority(flow.getPriority())
                .timeCreate(map(flow.getCreatedTime()))
                .timeModify(map(flow.getLastUpdated()))
                .pinned(flow.isPinned())
                .detectConnectedDevices(DetectConnectedDevicesMapper.INSTANCE.map(flow.getDetectConnectedDevices()))
                .build();
    }

    /**
     * Builds a flow from swap flow dto.
     *
     * @param flow a swap flow dto.
     * @return a flow.
     */
    public Flow buildFlow(SwapFlowDto flow) {
        Switch srcSwitch = Switch.builder().switchId(flow.getSourceSwitch()).build();
        Switch dstSwitch = Switch.builder().switchId(flow.getDestinationSwitch()).build();

        return Flow.builder()
                .flowId(flow.getFlowId())
                .srcSwitch(srcSwitch)
                .srcPort(flow.getSourcePort())
                .srcVlan(flow.getSourceVlan())
                .destSwitch(dstSwitch)
                .destPort(flow.getDestinationPort())
                .destVlan(flow.getDestinationVlan())
                .build();
    }
}
