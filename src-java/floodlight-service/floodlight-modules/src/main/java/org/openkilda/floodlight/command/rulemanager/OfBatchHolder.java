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
import org.openkilda.floodlight.error.SwitchNotFoundException;
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
import net.floodlightcontroller.core.IOFSwitch;
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
import java.util.function.BiFunction;
import java.util.function.Function;

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
        addSpeakerData(data, switchId, OfFlowConverter.INSTANCE::convertInstallFlowCommand, flowsMap,
                FlowSpeakerData::getCookie, true);
    }

    @Override
    public void addModifyFlow(FlowSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfFlowConverter.INSTANCE::convertModifyFlowCommand, flowsMap,
                FlowSpeakerData::getCookie, true);
    }

    @Override
    public void addDeleteFlow(FlowSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfFlowConverter.INSTANCE::convertDeleteFlowCommand, flowsMap,
                FlowSpeakerData::getCookie, false);
    }

    @Override
    public void addInstallMeter(MeterSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfMeterConverter.INSTANCE::convertInstallMeterCommand, metersMap,
                MeterSpeakerData::getMeterId, true);
    }

    @Override
    public void addModifyMeter(MeterSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfMeterConverter.INSTANCE::convertModifyMeterCommand, metersMap,
                MeterSpeakerData::getMeterId, true);
    }

    @Override
    public void addDeleteMeter(MeterSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfMeterConverter.INSTANCE::convertDeleteMeterCommand, metersMap,
                MeterSpeakerData::getMeterId, false);
    }

    @Override
    public void addInstallGroup(GroupSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfGroupConverter.INSTANCE::convertInstallGroupCommand, groupsMap,
                GroupSpeakerData::getGroupId, true);
    }

    @Override
    public void addModifyGroup(GroupSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfGroupConverter.INSTANCE::convertModifyGroupCommand, groupsMap,
                GroupSpeakerData::getGroupId, true);
    }

    @Override
    public void addDeleteGroup(GroupSpeakerData data, SwitchId switchId) {
        addSpeakerData(data, switchId, OfGroupConverter.INSTANCE::convertDeleteGroupCommand, groupsMap,
                GroupSpeakerData::getGroupId, false);
    }

    private <S extends SpeakerData, K> void addSpeakerData(S data, SwitchId switchId,
                                                           BiFunction<S, OFFactory, OFMessage> converter,
                                                           Map<K, S> mapping, Function<S, K> keyMapping,
                                                           boolean presenceBeVerified) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        IOFSwitch sw = iofSwitchService.getSwitch(dpId);
        if (sw == null) {
            otherFail(format("Can't process speaker command %s", data), new SwitchNotFoundException(dpId));
            return;
        }
        OFFactory factory = sw.getOFFactory();
        OFMessage message = converter.apply(data, factory);
        xidMapping.put(message.getXid(), data.getUuid());
        mapping.put(keyMapping.apply(data), data);
        BatchData batchData = BatchData.builder()
                .flow(data instanceof FlowSpeakerData)
                .meter(data instanceof MeterSpeakerData)
                .group(data instanceof GroupSpeakerData)
                .message(message)
                .presenceBeVerified(presenceBeVerified)
                .origin(data)
                .build();
        log.debug("Add batch data (uuid={}, xid={}) with dependencies: {}", data.getUuid(),
                batchData.getMessage().getXid(), data.getDependsOn());
        commandMap.put(data.getUuid(), batchData);
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
                .success(failedUuids.isEmpty() && failCause == null)
                .failedCommandIds(failedUuids)
                .build();
    }
}
