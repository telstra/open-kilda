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

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class OfBatchHolder implements OfEntityBatch {

    private final IOFSwitchService iofSwitchService;
    private final MessageContext messageContext;
    private final UUID commandId;
    private final SwitchId switchId;

    private final Map<UUID, String> failedUuids = new HashMap<>();
    private final Set<UUID> successUuids = new HashSet<>();
    private final Map<UUID, BatchData> commandMap = new HashMap<>();

    private final ExecutionGraph executionGraph = new ExecutionGraph();

    private final Map<MeterId, MeterSpeakerData> metersMap = new HashMap<>();
    private final Map<CookieBase, FlowSpeakerData> flowsMap = new HashMap<>();
    private final Map<GroupId, GroupSpeakerData> groupsMap = new HashMap<>();

    private Map<Long, UUID> xidMapping = new HashMap<>();

    public List<UUID> getCurrentStage() {
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

    public void recordSuccessUuid(UUID failedUuid) {
        log.debug("Record success for {}", failedUuid);
        successUuids.add(failedUuid);
    }

    /**
     * Record the given uuid as failed with corresponding error message.
     */
    public void recordFailedUuid(UUID failedUuid, String message) {
        log.debug("Record failed for {}, error message: {}", failedUuid, message);
        if (failedUuids.containsKey(failedUuid)) {
            // Append error messages.
            String previousMessage = failedUuids.get(failedUuid);
            failedUuids.put(failedUuid, previousMessage + "; " + message);
        } else {
            failedUuids.put(failedUuid, message);
        }
    }

    /**
     * Checks if action deps are satisfied.
     */
    public boolean canExecute(UUID uuid) {
        List<UUID> nodeInEdges = executionGraph.getNodeDependsOn(uuid);
        for (UUID dep : nodeInEdges) {
            if (!successUuids.contains(dep)) {
                return false;
            }
        }
        return commandMap.get(uuid) != null;
    }

    /**
     * Return a map of uuids (with description message) which block the given uuid.
     */
    public Map<UUID, String> getBlockingDependencies(UUID uuid) {
        Map<UUID, String> result = new HashMap<>();
        List<UUID> nodeInEdges = executionGraph.getNodeDependsOn(uuid);
        for (UUID dep : nodeInEdges) {
            if (!successUuids.contains(dep)) {
                if (failedUuids.containsKey(dep)) {
                    result.put(dep, failedUuids.get(dep));
                } else {
                    result.put(dep, "Not executed yet");
                }
            }
        }
        return result;
    }

    public BatchData getByUUid(UUID uuid) {
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

    public UUID popAwaitingXid(long xid) {
        return xidMapping.remove(xid);
    }

    public OfBatchHolder(IOFSwitchService iofSwitchService, MessageContext messageContext,
                         UUID commandId, SwitchId switchId) {
        this.iofSwitchService = iofSwitchService;
        this.messageContext = messageContext;
        this.commandId = commandId;
        this.switchId = switchId;
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
                .commandId(commandId)
                .switchId(switchId)
                .success(failedUuids.isEmpty())
                .failedCommandIds(failedUuids).build();
    }
}
