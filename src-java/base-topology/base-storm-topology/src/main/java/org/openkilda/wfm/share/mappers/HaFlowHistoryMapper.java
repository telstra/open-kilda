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

package org.openkilda.wfm.share.mappers;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.history.HaFlowDumpPayload;
import org.openkilda.messaging.payload.history.HaFlowHistoryEntry;
import org.openkilda.messaging.payload.history.HaFlowHistoryPayload;
import org.openkilda.messaging.payload.history.HaFlowPathPayload;
import org.openkilda.messaging.payload.history.HaSubFlowPayload;
import org.openkilda.model.FlowPath;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.history.HaFlowEvent;
import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpDataImpl;
import org.openkilda.model.history.HaFlowEventDump.HaSubFlowDumpWrapper;
import org.openkilda.wfm.share.history.model.DumpType;
import org.openkilda.wfm.share.history.model.HaFlowDumpData;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.share.history.model.HaFlowHistoryData;
import org.openkilda.wfm.share.history.model.HaFlowPathDump;
import org.openkilda.wfm.share.history.model.HaSubFlowDump;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(imports = { ZoneOffset.class, SwitchId.class, MeterId.class, GroupId.class })
public abstract class HaFlowHistoryMapper {

    public static HaFlowHistoryMapper INSTANCE = Mappers.getMapper(HaFlowHistoryMapper.class);

    @Mapping(source = "haFlowEventData.initiator", target = "actor")
    @Mapping(source = "haFlowEventData.event.description", target = "action")
    @Mapping(source = "time", target = "timestamp")
    @Mapping(target = "taskId", ignore = true)
    public abstract HaFlowEvent createHaFlowEvent(HaFlowEventData haFlowEventData);

    @Mapping(source = "time", target = "timestamp")
    @Mapping(source = "description", target = "details")
    @Mapping(target = "taskId", ignore = true)
    @Mapping(target = "data", ignore = true)
    public abstract HaFlowEventAction createHaFlowEventAction(HaFlowHistoryData haFlowHistoryData);

    @Mapping(source = "payloads", target = "payloads")
    @Mapping(source = "dumps", target = "dumps")
    @Mapping(source = "flowEvent.timestamp", target = "time")
    @Mapping(target = "timestampIso",
            expression = "java(flowEvent.getTimestamp().atOffset(ZoneOffset.UTC).toString())")
    public abstract HaFlowHistoryEntry createHaFlowHistoryEntry(
            HaFlowEvent flowEvent, List<HaFlowHistoryPayload> payloads, List<HaFlowDumpPayload> dumps);

    public abstract HaFlowEventDumpDataImpl createHaFlowEventDump(HaFlowDumpData haFlowDumpData);

    public String switchIdToString(SwitchId value) {
        return value == null ? null : value.toString();
    }

    public String pathIdToString(PathId value) {
        return value == null ? null : value.toString();
    }

    public String cookieToString(FlowSegmentCookie value) {
        return value == null ? null : value.toString();
    }

    public String groupIdToString(GroupId value) {
        return value == null ? null : value.toString();
    }

    public String meterIdToString(MeterId value) {
        return value == null ? null : value.toString();
    }

    /**
     * Creates an HA sub flows dump for persistence.
     * @param haSubFlowDump source
     * @return target
     */
    public HaFlowEventDump.HaSubFlowDumpWrapper createHaSubFlowsDump(List<HaSubFlowDump> haSubFlowDump) {
        if (haSubFlowDump == null) {
            return HaFlowEventDump.HaSubFlowDumpWrapper.empty();
        }

        return HaFlowEventDump.HaSubFlowDumpWrapper.builder()
                .haSubFlowDumpList(haSubFlowDump.stream()
                        .map(this::messagingToPersistence)
                        .collect(Collectors.toList()))
                .build();
    }

    public abstract HaFlowEventDump.HaSubFlowDump messagingToPersistence(HaSubFlowDump haSubFlowDump);


    @Mapping(source = "YPointSwitchId", target = "yPointSwitchId")
    @Mapping(source = "YPointMeterId", target = "yPointMeterId")
    @Mapping(source = "YPointGroupId", target = "yPointGroupId")
    public abstract HaFlowEventDump.HaFlowPathDump messagingToPersistence(HaFlowPathDump haFlowPathDump);

    /**
     * Transforms a payload from messaging module to a payload from persistence module.
     * @param value source
     * @return target
     */
    public List<List<HaFlowEventDump.PathNodePayload>> messagingToPersistence(List<List<PathNodePayload>> value) {
        if (value == null) {
            return Collections.emptyList();
        }

        return value.stream()
                .map(list -> list.stream().map(this::toPersistencePayload).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public abstract HaFlowEventDump.PathNodePayload toPersistencePayload(PathNodePayload value);

    public PathNodePayload persistenceToPayload(HaFlowEventDump.PathNodePayload value) {
        return new PathNodePayload(new SwitchId(value.getSwitchId()), value.getInputPort(), value.getOutputPort());
    }

    /**
     * Transforms a payload from persistence module to a payload from messaging module.
     * @param value source
     * @return target
     */
    public List<PathNodePayload> persistenceToPayload(List<HaFlowEventDump.PathNodePayload> value) {
        if (value == null) {
            return Collections.emptyList();
        }
        return value.stream().map(this::persistenceToPayload).collect(Collectors.toList());
    }

    @Mapping(target = "forwardPath", expression = "java(persistenceToPayload(haFlowEventDump.getForwardPath()))")
    @Mapping(target = "reversePath", expression = "java(persistenceToPayload(haFlowEventDump.getReversePath()))")
    @Mapping(target = "protectedForwardPath",
            expression = "java(persistenceToPayload(haFlowEventDump.getProtectedForwardPath()))")
    @Mapping(target = "protectedReversePath",
            expression = "java(persistenceToPayload(haFlowEventDump.getProtectedReversePath()))")
    public abstract HaFlowDumpPayload persistenceToPayload(HaFlowEventDump haFlowEventDump);

    @Mapping(target = "yPointSwitchId", source = "YPointSwitchId")
    @Mapping(target = "yPointMeterId", source = "YPointMeterId")
    @Mapping(target = "yPointGroupId", source = "YPointGroupId")
    public abstract HaFlowPathPayload persistenceToPayload(HaFlowEventDump.HaFlowPathDump haFlowPathDump);

    public abstract HaSubFlowPayload persistenceToPayload(HaFlowEventDump.HaSubFlowDump haSubFlowDump);

    /**
     * Creates an HA subflows payload list for NB.
     * @param haSubFlowDump source
     * @return target
     */
    public List<HaSubFlowPayload> persistenceToPayload(HaSubFlowDumpWrapper haSubFlowDump) {
        return haSubFlowDump.getHaSubFlowDumpList().stream()
                .map(this::persistenceToPayload)
                .collect(Collectors.toList());
    }

    public abstract List<List<PathNodePayload>> persistenceToPayloadList(
            List<List<HaFlowEventDump.PathNodePayload>> value);

    @Mapping(target = "timestampIso",
            expression = "java(haFlowEventAction.getTimestamp().atOffset(ZoneOffset.UTC).toString())")
    public abstract HaFlowHistoryPayload toHaFlowHistoryPayload(HaFlowEventAction haFlowEventAction);

    /**
     * Creates an HA-flow dump that represents the same HA-flow data but structured in a different way.
     * This representation is supposed to be used to store and pass around history events.
     * @param haFlow the source of the data
     * @param correlationId operation ID
     * @param dumpType dump type
     * @return dump of the HA-flow
     */
    public HaFlowDumpData toHaFlowDumpData(HaFlow haFlow, String correlationId, DumpType dumpType) {
        if (haFlow == null) {
            return HaFlowDumpData.empty();
        }

        return HaFlowDumpData.builder()
                .dumpType(dumpType)
                .taskId(correlationId)
                .haFlowId(haFlow.getHaFlowId())
                .affinityGroupId(haFlow.getAffinityGroupId())
                .allocateProtectedPath(haFlow.isAllocateProtectedPath())
                .description(haFlow.getDescription())
                .diverseGroupId(haFlow.getDiverseGroupId())
                .encapsulationType(haFlow.getEncapsulationType())
                .flowTimeCreate(haFlow.getTimeCreate())
                .flowTimeModify(haFlow.getTimeModify())
                .ignoreBandwidth(haFlow.isIgnoreBandwidth())
                .maxLatency(haFlow.getMaxLatency())
                .maxLatencyTier2(haFlow.getMaxLatencyTier2())
                .maximumBandwidth(haFlow.getMaximumBandwidth())
                .pathComputationStrategy(haFlow.getPathComputationStrategy())
                .periodicPings(haFlow.isPeriodicPings())
                .pinned(haFlow.isPinned())
                .priority(haFlow.getPriority())
                .sharedInnerVlan(haFlow.getSharedInnerVlan())
                .sharedOuterVlan(haFlow.getSharedOuterVlan())
                .sharedPort(haFlow.getSharedPort())
                .sharedSwitchId(haFlow.getSharedSwitchId())
                .status(haFlow.getStatus())
                .statusInfo(haFlow.getStatusInfo())
                .strictBandwidth(haFlow.isStrictBandwidth())
                .haSubFlows(toHaSubFlowDumps(haFlow))
                .forwardPath(toHaFlowPathDump(haFlow.getForwardPath()))
                .reversePath(toHaFlowPathDump(haFlow.getReversePath()))
                .protectedForwardPath(toHaFlowPathDump(haFlow.getProtectedForwardPath()))
                .protectedReversePath(toHaFlowPathDump(haFlow.getProtectedReversePath()))
                .build();
    }

    private List<HaSubFlowDump> toHaSubFlowDumps(HaFlow haFlow) {
        if (haFlow.getHaSubFlows() == null) {
            return Collections.emptyList();
        }

        return haFlow.getHaSubFlows().stream()
                .map(subFlow -> HaSubFlowDump.builder()
                        .haSubFlowId(subFlow.getHaSubFlowId())
                        .haFlowId(subFlow.getHaFlowId())
                        .haSubFlowId(subFlow.getHaSubFlowId())
                        .status(subFlow.getStatus())
                        .endpointSwitchId(subFlow.getEndpointSwitchId())
                        .endpointPort(subFlow.getEndpointPort())
                        .endpointVlan(subFlow.getEndpointVlan())
                        .endpointInnerVlan(subFlow.getEndpointInnerVlan())
                        .description(subFlow.getDescription())
                        .timeCreate(subFlow.getTimeCreate())
                        .timeModify(subFlow.getTimeModify())
                        .build())
                .collect(Collectors.toList());
    }

    private List<PathNodePayload> toPathNodePayload(HaFlow haFlow, FlowPath flowPath) {
        if (haFlow == null || flowPath == null || flowPath.getSegments() == null) {
            return Collections.emptyList();
        }

        return FlowPathMapper.INSTANCE.mapToPathNodes(
                FlowSideAdapter.makeIngressAdapter(haFlow, flowPath).getEndpoint(),
                flowPath.getSegments(),
                FlowSideAdapter.makeEgressAdapter(haFlow, flowPath).getEndpoint());
    }

    private List<List<PathNodePayload>> toPathNodePayloadList(HaFlow haFlow, HaFlowPath haFlowPath) {
        if (haFlow == null || haFlowPath == null || haFlowPath.getSubPaths() == null
                || haFlowPath.getSubPaths().size() == 0) {
            return Collections.emptyList();
        }

        return haFlowPath.getSubPaths().stream()
                .map(subPath -> toPathNodePayload(haFlow, subPath))
                .collect(Collectors.toList());
    }

    private HaFlowPathDump toHaFlowPathDump(HaFlowPath haFlowPath) {
        if (haFlowPath == null) {
            return HaFlowPathDump.empty();
        }

        return HaFlowPathDump.builder()
                .haPathId(haFlowPath.getHaPathId())
                .sharedSwitchId(haFlowPath.getSharedSwitchId())
                .yPointSwitchId(haFlowPath.getYPointSwitchId())
                .timeModify(haFlowPath.getTimeModify())
                .timeCreate(haFlowPath.getTimeCreate())
                .status(haFlowPath.getStatus())
                .ignoreBandwidth(haFlowPath.isIgnoreBandwidth())
                .cookie(haFlowPath.getCookie())
                .sharedPointMeterId(haFlowPath.getSharedPointMeterId())
                .yPointMeterId(haFlowPath.getYPointMeterId())
                .yPointGroupId(haFlowPath.getYPointGroupId())
                .bandwidth(haFlowPath.getBandwidth())
                .paths(toPathNodePayloadList(haFlowPath.getHaFlow(), haFlowPath))
                .haSubFlows(toHaSubFlowDumpList(haFlowPath.getHaSubFlows()))
                .build();
    }

    private List<HaSubFlowDump> toHaSubFlowDumpList(List<HaSubFlow> haSubFlows) {
        if (haSubFlows == null) {
            return Collections.emptyList();
        }

        return haSubFlows.stream().map(this::toHaSubFlowDump).collect(Collectors.toList());
    }

    private HaSubFlowDump toHaSubFlowDump(HaSubFlow haSubFlow) {
        if (haSubFlow == null) {
            return HaSubFlowDump.empty();
        }

        return HaSubFlowDump.builder()
                .haSubFlowId(haSubFlow.getHaSubFlowId())
                .timeModify(haSubFlow.getTimeModify())
                .timeCreate(haSubFlow.getTimeCreate())
                .endpointSwitchId(haSubFlow.getEndpointSwitchId())
                .status(haSubFlow.getStatus())
                .endpointVlan(haSubFlow.getEndpointVlan())
                .endpointInnerVlan(haSubFlow.getEndpointInnerVlan())
                .endpointPort(haSubFlow.getEndpointPort())
                .description(haSubFlow.getDescription())
                .build();
    }
}
