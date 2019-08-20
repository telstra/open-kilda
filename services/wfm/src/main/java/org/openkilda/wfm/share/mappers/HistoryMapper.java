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
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.history.FlowDumpPayload;
import org.openkilda.messaging.payload.history.FlowEventPayload;
import org.openkilda.messaging.payload.history.FlowHistoryPayload;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.model.history.FlowDump;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowHistory;
import org.openkilda.model.history.PortHistory;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.PortHistoryData;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.TargetType;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(uses = {FlowPathMapper.class})
public abstract class HistoryMapper {
    public static final HistoryMapper INSTANCE = Mappers.getMapper(HistoryMapper.class);

    @Mapping(target = "timestamp", expression = "java(flowEvent.getTimestamp().getEpochSecond())")
    public abstract FlowEventPayload map(FlowEvent flowEvent);

    @Mapping(target = "timestamp", expression = "java(flowHistory.getTimestamp().getEpochSecond())")
    public abstract FlowHistoryPayload map(FlowHistory flowHistory);

    @Mapping(target = "forwardCookie",
            expression = "java(flowDump.getForwardCookie() != null ? flowDump.getForwardCookie().getValue() : null)")
    @Mapping(target = "reverseCookie",
            expression = "java(flowDump.getReverseCookie() != null ? flowDump.getReverseCookie().getValue() : null)")
    @Mapping(target = "forwardMeterId",
            expression = "java(flowDump.getForwardMeterId() != null ? flowDump.getForwardMeterId().getValue() : null)")
    @Mapping(target = "reverseMeterId",
            expression = "java(flowDump.getReverseMeterId() != null ? flowDump.getReverseMeterId().getValue() : null)")
    public abstract FlowDumpPayload map(FlowDump flowDump);

    @Mapping(target = "type", expression = "java(dumpData.getDumpType().getType())")
    public abstract FlowDump map(FlowDumpData dumpData);

    /**
     * Note: you have to additionally set {@link org.openkilda.wfm.share.history.model.FlowDumpData.DumpType}
     * to the dump data.
     */
    public FlowDumpData map(Flow flow) {
        return map(flow, flow.getForwardPath(), flow.getReversePath());
    }

    /**
     * Note: you have to additionally set {@link org.openkilda.wfm.share.history.model.FlowDumpData.DumpType}
     * to the dump data.
     */
    @Mapping(target = "sourceSwitch", expression = "java(flow.getSrcSwitch().getSwitchId())")
    @Mapping(target = "destinationSwitch", expression = "java(flow.getDestSwitch().getSwitchId())")
    @Mapping(source = "flow.srcPort", target = "sourcePort")
    @Mapping(source = "flow.destPort", target = "destinationPort")
    @Mapping(source = "flow.srcVlan", target = "sourceVlan")
    @Mapping(source = "flow.destVlan", target = "destinationVlan")
    @Mapping(source = "flow.flowId", target = "flowId")
    @Mapping(source = "flow.bandwidth", target = "bandwidth")
    @Mapping(source = "flow.ignoreBandwidth", target = "ignoreBandwidth")
    @Mapping(source = "forward.cookie", target = "forwardCookie")
    @Mapping(source = "reverse.cookie", target = "reverseCookie")
    @Mapping(source = "forward.meterId", target = "forwardMeterId")
    @Mapping(source = "reverse.meterId", target = "reverseMeterId")
    @Mapping(source = "forward.status", target = "forwardStatus")
    @Mapping(source = "reverse.status", target = "reverseStatus")
    @BeanMapping(ignoreByDefault = true)
    public abstract FlowDumpData map(Flow flow, FlowPath forward, FlowPath reverse);

    /**
     * Note: you have to additionally set {@link org.openkilda.wfm.share.history.model.FlowDumpData.DumpType}
     * to the dump data.
     */
    @Mapping(target = "sourceSwitch", expression = "java(flow.getSrcSwitch().getSwitchId())")
    @Mapping(target = "destinationSwitch", expression = "java(flow.getDestSwitch().getSwitchId())")
    @Mapping(source = "flow.srcPort", target = "sourcePort")
    @Mapping(source = "flow.destPort", target = "destinationPort")
    @Mapping(source = "flow.srcVlan", target = "sourceVlan")
    @Mapping(source = "flow.destVlan", target = "destinationVlan")
    @Mapping(source = "flow.flowId", target = "flowId")
    @Mapping(source = "flow.bandwidth", target = "bandwidth")
    @Mapping(source = "flow.ignoreBandwidth", target = "ignoreBandwidth")
    @Mapping(target = "forwardCookie", expression =
            "java(org.openkilda.model.Cookie.buildForwardCookie(resources.getUnmaskedCookie()))")
    @Mapping(target = "reverseCookie", expression =
            "java(org.openkilda.model.Cookie.buildReverseCookie(resources.getUnmaskedCookie()))")
    @Mapping(source = "resources.forward.meterId", target = "forwardMeterId")
    @Mapping(source = "resources.reverse.meterId", target = "reverseMeterId")
    @BeanMapping(ignoreByDefault = true)
    public abstract FlowDumpData map(Flow flow, FlowResources resources);


    @Mapping(source = "time", target = "timestamp")
    @Mapping(source = "description", target = "details")
    public abstract FlowHistory map(FlowHistoryData historyData);

    @Mapping(source = "eventData.initiator", target = "actor")
    @Mapping(source = "eventData.event.description", target = "action")
    @Mapping(source = "time", target = "timestamp")
    public abstract FlowEvent map(FlowEventData eventData);

    @Mapping(target = "switchId", expression = "java(data.getEndpoint().getDatapath())")
    @Mapping(target = "portNumber", expression = "java(data.getEndpoint().getPortNumber())")
    public abstract PortHistory map(PortHistoryData data);

    @Mapping(target = "time", expression = "java(portHistory.getTime().getEpochSecond())")
    public abstract PortHistoryPayload map(PortHistory portHistory);

    /**
     * Adds string representation of flow path into {@link FlowDumpData}.
     */
    @AfterMapping
    public FlowDumpData map(Flow flow, @TargetType FlowDumpData dumpData) throws JsonProcessingException {
        List<PathNodePayload> forwardNodes = FlowPathMapper.INSTANCE.mapToPathNodes(flow.getForwardPath());
        List<PathNodePayload> reverseNodes = FlowPathMapper.INSTANCE.mapToPathNodes(flow.getReversePath());

        dumpData.setForwardPath(Utils.MAPPER.writeValueAsString(forwardNodes));
        dumpData.setForwardPath(Utils.MAPPER.writeValueAsString(reverseNodes));

        return dumpData;
    }

    public String map(SwitchId switchId) {
        return switchId.toString();
    }
}
