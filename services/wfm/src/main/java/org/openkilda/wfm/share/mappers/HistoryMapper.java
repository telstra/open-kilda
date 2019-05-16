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
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.model.history.FlowDump;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowHistory;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.TargetType;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(uses = {FlowMapper.class, FlowPathMapper.class})
public abstract class HistoryMapper {
    public static final HistoryMapper INSTANCE = Mappers.getMapper(HistoryMapper.class);

    @Mapping(target = "timestamp", expression = "java(flowEvent.getTimestamp().getEpochSecond())")
    public abstract FlowEventPayload map(FlowEvent flowEvent);

    @Mapping(target = "timestamp", expression = "java(flowHistory.getTimestamp().getEpochSecond())")
    public abstract FlowHistoryPayload map(FlowHistory flowHistory);

    public abstract FlowDumpPayload map(FlowDump flowDump);

    @Mapping(target = "type", expression = "java(dumpData.getDumpType().getType())")
    public abstract FlowDump map(FlowDumpData dumpData);

    /**
     * Note: you have to additionally set {@link org.openkilda.wfm.share.history.model.FlowDumpData.DumpType}
     * to the dump data.
     */
    @Mapping(target = "sourceSwitch", expression = "java(flow.getSrcSwitch().getSwitchId())")
    @Mapping(target = "destinationSwitch", expression = "java(flow.getDestSwitch().getSwitchId())")
    @Mapping(source = "srcPort", target = "sourcePort")
    @Mapping(source = "destPort", target = "destinationPort")
    @Mapping(source = "srcVlan", target = "sourceVlan")
    @Mapping(source = "destVlan", target = "destinationVlan")
    @Mapping(source = "flowId", target = "flowId")
    @Mapping(source = "bandwidth", target = "bandwidth")
    @Mapping(source = "ignoreBandwidth", target = "ignoreBandwidth")
    @Mapping(target = "forwardCookie", expression = "java(flow.getForwardPath().getCookie())")
    @Mapping(target = "reverseCookie", expression = "java(flow.getReversePath().getCookie())")
    @Mapping(target = "forwardMeterId", expression = "java(flow.getReversePath().getMeterId())")
    @Mapping(target = "reverseMeterId", expression = "java(flow.getReversePath().getMeterId())")
    @Mapping(target = "forwardStatus", expression = "java(flow.getReversePath().getStatus())")
    @Mapping(target = "reverseStatus", expression = "java(flow.getReversePath().getStatus())")
    @BeanMapping(ignoreByDefault = true)
    public abstract FlowDumpData map(Flow flow);

    @Mapping(source = "time", target = "timestamp")
    @Mapping(source = "description", target = "details")
    public abstract FlowHistory map(FlowHistoryData historyData);

    @Mapping(target = "actor", expression = "java(eventData.getInitiator().name())")
    @Mapping(target = "action", expression = "java(eventData.getEvent().getDescription())")
    @Mapping(source = "time", target = "timestamp")
    public abstract FlowEvent map(FlowEventData eventData);

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
