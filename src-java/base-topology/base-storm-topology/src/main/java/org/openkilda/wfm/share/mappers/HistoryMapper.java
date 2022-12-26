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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.payload.history.FlowDumpPayload;
import org.openkilda.messaging.payload.history.FlowHistoryEntry;
import org.openkilda.messaging.payload.history.FlowHistoryPayload;
import org.openkilda.messaging.payload.history.FlowStatusTimestampsEntry;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorPointStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSegmentCookieBuilder;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventDump;
import org.openkilda.model.history.FlowStatusView;
import org.openkilda.model.history.PortEvent;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.PortEventData;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Mapper(uses = {FlowPathMapper.class}, imports = ZoneOffset.class)
public abstract class HistoryMapper {
    public static final HistoryMapper INSTANCE = Mappers.getMapper(HistoryMapper.class);

    @Mapping(target = "payload", source = "payload")
    @Mapping(target = "dumps", source = "dumps")
    @Mapping(target = "timestampIso",
             expression = "java(flowEvent.getTimestamp().atOffset(ZoneOffset.UTC).toString())")
    public abstract FlowHistoryEntry map(
            FlowEvent flowEvent, List<FlowHistoryPayload> payload, List<FlowDumpPayload> dumps);

    @Mapping(target = "timestampIso",
            expression = "java(action.getTimestamp().atOffset(ZoneOffset.UTC).toString())")
    public abstract FlowHistoryPayload map(FlowEventAction action);

    /**
     * Map {@link FlowEventDump} into {@link FlowDumpPayload}.
     */
    public FlowDumpPayload map(FlowEventDump dump) {
        FlowDumpPayload result = generatedMap(dump);

        long forwardCookie = fallbackIfNull(mapCookie(dump.getForwardCookie()), 0L);
        long reverseCookie = fallbackIfNull(mapCookie(dump.getReverseCookie()), 0L);

        result.setForwardCookie(forwardCookie);
        result.setReverseCookie(reverseCookie);
        result.setForwardCookieHex(Long.toHexString(forwardCookie));
        result.setReverseCookieHex(Long.toHexString(reverseCookie));
        return result;
    }

    @Mapping(target = "type", source = "dumpType")
    @Mapping(target = "taskId", ignore = true)
    @Mapping(target = "data", ignore = true)
    public abstract FlowEventDump map(FlowDumpData dump);

    /**
     * Note: you have to additionally set {@link org.openkilda.wfm.share.history.model.FlowDumpData.DumpType}
     * to the dump data.
     */
    public FlowDumpData map(Flow flow, FlowPath forward, FlowPath reverse, DumpType dumpType) {
        FlowDumpData result = generatedMap(flow, forward, reverse, dumpType);
        result.setMirrorPointStatuses(map(getFlowMirrors(flow)));

        return result;
    }

    /**
     * Note: you have to additionally set {@link org.openkilda.wfm.share.history.model.FlowDumpData.DumpType}
     * to the dump data.
     */
    public FlowDumpData map(Flow flow, FlowResources resources, DumpType dumpType) {
        FlowDumpData result = generatedMap(flow, resources, dumpType);

        result.setMirrorPointStatuses(map(getFlowMirrors(flow)));

        FlowSegmentCookieBuilder cookieBuilder = FlowSegmentCookie.builder()
                .flowEffectiveId(resources.getUnmaskedCookie());
        result.setForwardCookie(cookieBuilder.direction(FlowPathDirection.FORWARD).build());
        result.setReverseCookie(cookieBuilder.direction(FlowPathDirection.REVERSE).build());

        return result;
    }

    @Mapping(source = "time", target = "timestamp")
    @Mapping(source = "description", target = "details")
    @Mapping(target = "taskId", ignore = true)
    @Mapping(target = "data", ignore = true)
    public abstract FlowEventAction map(FlowHistoryData historyData);

    @Mapping(source = "eventData.initiator", target = "actor")
    @Mapping(source = "eventData.event.description", target = "action")
    @Mapping(source = "time", target = "timestamp")
    @Mapping(target = "taskId", ignore = true)
    public abstract FlowEvent map(FlowEventData eventData);

    @Mapping(target = "switchId", source = "endpoint.datapath")
    @Mapping(target = "portNumber", source = "endpoint.portNumber")
    @Mapping(target = "recordId", ignore = true)
    @Mapping(target = "data", ignore = true)
    public abstract PortEvent map(PortEventData data);

    @Mapping(target = "timestamp", ignore = true)
    @Mapping(target = "id", source = "recordId")
    public abstract PortHistoryPayload map(PortEvent portEvent);

    public String map(SwitchId switchId) {
        return switchId.toString();
    }

    @Mapping(target = "statusChangeTimestamp", source = "timestamp")
    @Mapping(target = "timestamp", ignore = true)
    public abstract FlowStatusTimestampsEntry map(FlowStatusView flowStatusesImmutableView);

    public abstract List<MirrorPointStatus> map(List<FlowMirror> flowMirrorPaths);

    @Mapping(source = "flowMirrorId", target = "mirrorPointId")
    public abstract MirrorPointStatus map(FlowMirror flowMirrorPath);

    /**
     * Convert {@link PathId} to {@link String}.
     */
    public String map(PathId pathId) {
        if (pathId == null) {
            return null;
        }
        return pathId.getId();
    }

    @Mapping(source = "flow.srcSwitch.switchId", target = "sourceSwitch")
    @Mapping(source = "flow.destSwitch.switchId", target = "destinationSwitch")
    @Mapping(source = "flow.srcPort", target = "sourcePort")
    @Mapping(source = "flow.destPort", target = "destinationPort")
    @Mapping(source = "flow.srcVlan", target = "sourceVlan")
    @Mapping(source = "flow.destVlan", target = "destinationVlan")
    @Mapping(source = "flow.srcInnerVlan", target = "sourceInnerVlan")
    @Mapping(source = "flow.destInnerVlan", target = "destinationInnerVlan")
    @Mapping(source = "flow.flowId", target = "flowId")
    @Mapping(source = "flow.bandwidth", target = "bandwidth")
    @Mapping(source = "flow.ignoreBandwidth", target = "ignoreBandwidth")
    @Mapping(source = "flow.strictBandwidth", target = "strictBandwidth")
    @Mapping(source = "flow.loopSwitchId", target = "loopSwitchId")
    @Mapping(source = "flow.maxLatency", target = "maxLatency")
    @Mapping(source = "flow.maxLatencyTier2", target = "maxLatencyTier2")
    @Mapping(source = "flow.priority", target = "priority")
    @Mapping(source = "resources.forward.meterId", target = "forwardMeterId")
    @Mapping(source = "resources.reverse.meterId", target = "reverseMeterId")
    @Mapping(source = "dumpType", target = "dumpType")
    @Mapping(target = "forwardCookie", ignore = true)
    @Mapping(target = "reverseCookie", ignore = true)
    @Mapping(target = "forwardStatus", ignore = true)
    @Mapping(target = "reverseStatus", ignore = true)
    @Mapping(target = "mirrorPointStatuses", ignore = true)
    @Mapping(target = "forwardPath", expression = "java(mapPath(flow, flow.getForwardPath()))")
    @Mapping(target = "reversePath", expression = "java(mapPath(flow, flow.getReversePath()))")
    protected abstract FlowDumpData generatedMap(Flow flow, FlowResources resources, DumpType dumpType);

    @Mapping(target = "forwardCookie", ignore = true)
    @Mapping(target = "reverseCookie", ignore = true)
    @Mapping(target = "forwardCookieHex", ignore = true)
    @Mapping(target = "reverseCookieHex", ignore = true)
    protected abstract FlowDumpPayload generatedMap(FlowEventDump dump);

    /**
     * Note: you have to additionally set {@link org.openkilda.wfm.share.history.model.FlowDumpData.DumpType}
     * to the dump data.
     */
    @Mapping(source = "flow.srcSwitch.switchId", target = "sourceSwitch")
    @Mapping(source = "flow.destSwitch.switchId", target = "destinationSwitch")
    @Mapping(source = "flow.srcPort", target = "sourcePort")
    @Mapping(source = "flow.destPort", target = "destinationPort")
    @Mapping(source = "flow.srcVlan", target = "sourceVlan")
    @Mapping(source = "flow.destVlan", target = "destinationVlan")
    @Mapping(source = "flow.srcInnerVlan", target = "sourceInnerVlan")
    @Mapping(source = "flow.destInnerVlan", target = "destinationInnerVlan")
    @Mapping(source = "flow.flowId", target = "flowId")
    @Mapping(source = "flow.bandwidth", target = "bandwidth")
    @Mapping(source = "flow.ignoreBandwidth", target = "ignoreBandwidth")
    @Mapping(source = "flow.strictBandwidth", target = "strictBandwidth")
    @Mapping(source = "flow.allocateProtectedPath", target = "allocateProtectedPath")
    @Mapping(source = "flow.pinned", target = "pinned")
    @Mapping(source = "flow.periodicPings", target = "periodicPings")
    @Mapping(source = "flow.encapsulationType", target = "encapsulationType")
    @Mapping(source = "flow.pathComputationStrategy", target = "pathComputationStrategy")
    @Mapping(source = "flow.maxLatency", target = "maxLatency")
    @Mapping(source = "flow.maxLatencyTier2", target = "maxLatencyTier2")
    @Mapping(source = "flow.priority", target = "priority")
    @Mapping(source = "flow.loopSwitchId", target = "loopSwitchId")
    @Mapping(source = "forward.cookie", target = "forwardCookie")
    @Mapping(source = "reverse.cookie", target = "reverseCookie")
    @Mapping(source = "forward.meterId", target = "forwardMeterId")
    @Mapping(source = "reverse.meterId", target = "reverseMeterId")
    @Mapping(source = "forward.status", target = "forwardStatus")
    @Mapping(source = "reverse.status", target = "reverseStatus")
    @Mapping(target = "forwardPath", expression = "java(mapPath(flow, forward))")
    @Mapping(target = "reversePath", expression = "java(mapPath(flow, reverse))")
    @Mapping(source = "dumpType", target = "dumpType")
    @Mapping(target = "mirrorPointStatuses", ignore = true)
    public abstract FlowDumpData generatedMap(Flow flow, FlowPath forward, FlowPath reverse, DumpType dumpType);

    /**
     * Adds string representation of flow path into {@link FlowDumpData}.
     */
    protected String mapPath(Flow flow, FlowPath path) {
        try {
            return Utils.MAPPER.writeValueAsString(FlowPathMapper.INSTANCE.mapToPathNodes(flow, path));
        } catch (JsonProcessingException ex) {
            log.error("Unable to map the path: {}", path, ex);
            return null;
        }
    }

    /**
     * Map {@link Instant} into {@link long}.
     */
    public long mapTimestamp(Instant value) {
        if (value == null) {
            return 0;
        }
        return value.getEpochSecond();
    }

    /**
     * Map {@link Cookie} into {@link long}.
     */
    public Long mapCookie(Cookie value) {
        if (value == null) {
            return null;
        }
        return value.getValue();
    }

    /**
     * Map {@link MeterId} into {@link long}.
     */
    public Long mapMeterId(MeterId value) {
        if (value == null) {
            return null;
        }
        return value.getValue();
    }

    /**
     * Map {@link DumpType} into {@link String}.
     */
    public String mapDumpType(DumpType value) {
        if (value == null) {
            return null;
        }
        return value.getType();
    }

    private <T> T fallbackIfNull(T value, T fallback) {
        if (value == null) {
            return fallback;
        }
        return value;
    }

    private static List<FlowMirror> getFlowMirrors(Flow flow) {
        return flow.getPaths().stream()
                .map(FlowPath::getFlowMirrorPointsSet)
                .flatMap(Collection::stream)
                .map(FlowMirrorPoints::getFlowMirrors)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}
