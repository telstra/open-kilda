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

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.OfEntityBatch;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.floodlight.converter.rulemanager.OfFlowConverter;
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
import org.openkilda.rulemanager.SpeakerData;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

    private final Map<Long, UUID> xidMapping = new HashMap<>();

    private Throwable failCause;

    public List<UUID> getCurrentStage() {
        return executionGraph.getCurrent();
    }

    public boolean nextStage() {
        return executionGraph.nextStage();
    }


    public void recordSuccessUuid(UUID successUuid) {
        log.debug("Record success for {}", successUuid);
        successUuids.add(successUuid);
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
     * Process other fail during command processing.
     */
    public void otherFail(String message, Throwable throwable) {
        log.error(message, throwable);
        this.failCause = throwable;
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
                } else if (!commandMap.containsKey(dep)) {
                    result.put(dep, "Missing in the batch");
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

    /**
     * Get speaker data by UUID.
     */
    public SpeakerData getSpeakerDataByUUid(UUID uuid) {
        return Stream.of(flowsMap.values(), metersMap.values(), groupsMap.values())
                .flatMap(Collection::stream)
                .filter(data -> uuid.equals(data.getUuid()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(format("Can't find speaker data with uuid %s", uuid)));
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
        log.trace("popAwaitingXid for {}, current uuid: {}", xid, xidMapping.get(xid));
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
        OFMessage message = OfFlowConverter.INSTANCE.convertInstallFlowCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        flowsMap.put(data.getCookie(), data);
        BatchData batchData = BatchData.builder().flow(true).message(message).presenceBeVerified(true).build();
        addBatchData(data.getUuid(), batchData, data.getDependsOn());
    }

    @Override
    public void addModifyFlow(FlowSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfFlowConverter.INSTANCE.convertModifyFlowCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        BatchData batchData = BatchData.builder().flow(true).message(message).presenceBeVerified(true).build();
        commandMap.put(data.getUuid(), batchData);
        flowsMap.put(data.getCookie(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addDeleteFlow(FlowSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfFlowConverter.INSTANCE.convertDeleteFlowCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        flowsMap.put(data.getCookie(), data);
        BatchData batchData = BatchData.builder().flow(true).message(message).presenceBeVerified(false).build();
        addBatchData(data.getUuid(), batchData, data.getDependsOn());
    }

    @Override
    public void addInstallMeter(MeterSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfMeterConverter.INSTANCE.convertInstallMeterCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        metersMap.put(data.getMeterId(), data);
        BatchData batchData = BatchData.builder().meter(true).message(message).presenceBeVerified(true).build();
        addBatchData(data.getUuid(), batchData, data.getDependsOn());
    }

    @Override
    public void addModifyMeter(MeterSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfMeterConverter.INSTANCE.convertModifyMeterCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        BatchData batchData = BatchData.builder().meter(true).message(message).presenceBeVerified(true).build();
        commandMap.put(data.getUuid(), batchData);
        metersMap.put(data.getMeterId(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addDeleteMeter(MeterSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfMeterConverter.INSTANCE.convertDeleteMeterCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        metersMap.put(data.getMeterId(), data);
        BatchData batchData = BatchData.builder().meter(true).message(message).presenceBeVerified(false).build();
        addBatchData(data.getUuid(), batchData, data.getDependsOn());
    }

    @Override
    public void addInstallGroup(GroupSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfGroupConverter.INSTANCE.convertInstallGroupCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        groupsMap.put(data.getGroupId(), data);
        BatchData batchData = BatchData.builder().group(true).message(message).presenceBeVerified(true).build();
        addBatchData(data.getUuid(), batchData, data.getDependsOn());
    }

    @Override
    public void addModifyGroup(GroupSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfGroupConverter.INSTANCE.convertModifyGroupCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        BatchData batchData = BatchData.builder().group(true).message(message).presenceBeVerified(true).build();
        commandMap.put(data.getUuid(), batchData);
        groupsMap.put(data.getGroupId(), data);
        executionGraph.add(data.getUuid(), data.getDependsOn());
    }

    @Override
    public void addDeleteGroup(GroupSpeakerData data, SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        OFFactory factory = iofSwitchService.getSwitch(dpId).getOFFactory();
        OFMessage message = OfGroupConverter.INSTANCE.convertDeleteGroupCommand(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        groupsMap.put(data.getGroupId(), data);
        BatchData batchData = BatchData.builder().group(true).message(message).presenceBeVerified(false).build();
        addBatchData(data.getUuid(), batchData, data.getDependsOn());
    }

    private void addBatchData(UUID uuid, BatchData batchData, Collection<UUID> dependsOn) {
        log.debug("Add batch data (uuid={}, xid={}) with dependencies: {}", uuid, batchData.getMessage().getXid(),
                dependsOn);
        commandMap.put(uuid, batchData);
        executionGraph.add(uuid, dependsOn);
    }

    /**
     * Generates result of execution.
     */
    public SpeakerCommandResponse getResult() {
        return SpeakerCommandResponse.builder()
                .messageContext(messageContext)
                .commandId(commandId)
                .switchId(switchId)
                .success(failedUuids.isEmpty() && failCause == null)
                .failedCommandIds(failedUuids).build();
    }
}
