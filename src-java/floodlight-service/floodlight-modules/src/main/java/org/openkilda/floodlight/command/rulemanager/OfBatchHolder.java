/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.command.rulemanager;

import org.openkilda.floodlight.api.request.rulemanager.OfEntityBatch;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.floodlight.converter.rulemanager.OfFlowModConverter;
import org.openkilda.floodlight.converter.rulemanager.OfGroupConverter;
import org.openkilda.floodlight.converter.rulemanager.OfMeterConverter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OfBatchHolder implements OfEntityBatch {

    private final IOFSwitchService iofSwitchService;
    private MessageContext messageContext;

    private Map<String, String> failedUuids = new HashMap<>();
    private Set<String> successUuids = new HashSet<>();
    private final Map<String, BatchData> commandMap = new HashMap<>();

    private ExecutionGraph executionGraph = new ExecutionGraph();

    private final Map<MeterId, MeterSpeakerData> metersMap = new HashMap<>();
    private final Map<CookieBase, FlowSpeakerData> flowsMap = new HashMap<>();
    private final Map<GroupId, GroupSpeakerData> groupsMap = new HashMap<>();

    private Map<Long, String> xidMapping = new HashMap<>();

    public List<String> getCurrentStage() {
        return executionGraph.getCurrent();
    }

    public boolean nextStage() {
        return executionGraph.nextStage();
    }

    /**
     * Reset in-flight xids.
     */
    public void resetXids() {
        xidMapping = new HashMap<>();
    }

    public void recordSuccessUuid(String failedUuid) {
        successUuids.add(failedUuid);
    }

    public void recordFailedUuid(String failedUuid, String message) {
        failedUuids.put(failedUuid, message);
    }

    /**
     * Checks if action deps are satisfied.
     */
    public boolean canExecute(String uuid) {
        List<String> nodeInEdges = executionGraph.getNodeDependsOn(uuid);
        for (String dep : nodeInEdges) {
            if (!successUuids.contains(dep)) {
                return false;
            }
        }
        return true;
    }



    public BatchData getByUUid(String uuid) {
        return commandMap.get(uuid);
    }

    public MeterSpeakerData getByMeterId(MeterId meterId) {
        return metersMap.get(meterId);
    }

    public FlowSpeakerData getByCookie(CookieBase cookieBase) {
        return flowsMap.get(cookieBase);
    }

    public GroupSpeakerData getByGroupId(GroupId groupId) {
        return groupsMap.get(groupId);
    }

    public String popAwaitingXid(long xid) {
        return xidMapping.remove(xid);
    }

    public OfBatchHolder(IOFSwitchService iofSwitchService, MessageContext messageContext) {
        this.iofSwitchService = iofSwitchService;
        this.messageContext = messageContext;
    }

    @Override
    public void addInstallFlow(FlowSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfFlowModConverter.INSTANCE.convertInstallFlowCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        commandMap.put(data.getUuid(), BatchData.builder().flow(true).message(message).build());
        flowsMap.put(data.getCookie(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addDeleteFlow(FlowSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfFlowModConverter.INSTANCE.convertDeleteFlowCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        commandMap.put(data.getUuid(), BatchData.builder().flow(true).message(message).build());
        flowsMap.put(data.getCookie(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addInstallMeter(MeterSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfMeterConverter.INSTANCE.convertInstallMeterCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        commandMap.put(data.getUuid(), BatchData.builder().meter(true).message(message).build());
        metersMap.put(data.getMeterId(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addDeleteMeter(MeterSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfMeterConverter.INSTANCE.convertDeleteMeterCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        commandMap.put(data.getUuid(), BatchData.builder().meter(true).message(message).build());
        metersMap.put(data.getMeterId(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addInstallGroup(GroupSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfGroupConverter.INSTANCE.convertInstallGroupCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        commandMap.put(data.getUuid(), BatchData.builder().group(true).message(message).build());
        groupsMap.put(data.getGroupId(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addDeleteGroup(GroupSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfGroupConverter.INSTANCE.convertDeleteGroupCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        commandMap.put(data.getUuid(), BatchData.builder().group(true).message(message).build());
        groupsMap.put(data.getGroupId(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    /**
     * Generates result of execution.
     */
    public SpeakerCommandResponse getResult() {
        return SpeakerCommandResponse.builder()
                .messageContext(messageContext)
                .success(failedUuids.isEmpty())
                .failedUuids(failedUuids).build();
    }
}
