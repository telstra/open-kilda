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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import static java.util.Collections.emptyList;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.NorthboundResponseCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
@Slf4j
public abstract class FlowProcessingWithSpeakerCommandsFsm<T extends AbstractStateMachine<T, S, E, C>, S, E, C,
        R extends NorthboundResponseCarrier & HistoryUpdateCarrier, L extends FlowProcessingEventListener>
        extends FlowProcessingWithHistorySupportFsm<T, S, E, C, R, L> {
    protected final String flowId;

    private final Map<UUID, SwitchId> pendingCommands = new HashMap<>();
    private final Map<UUID, Integer> retriedCommands = new HashMap<>();
    private final Map<UUID, SpeakerCommandResponse> failedCommands = new HashMap<>();
    private final Map<UUID, BaseSpeakerCommandsRequest> speakerCommands = new HashMap<>();

    protected FlowProcessingWithSpeakerCommandsFsm(
            @NonNull E nextEvent, @NonNull E errorEvent, @NonNull CommandContext commandContext, @NonNull R carrier,
            @NonNull String flowId) {
        this(nextEvent, errorEvent, commandContext, carrier, flowId, emptyList());
    }

    protected FlowProcessingWithSpeakerCommandsFsm(
            @NonNull E nextEvent, @NonNull E errorEvent, @NonNull CommandContext commandContext, @NonNull R carrier,
            @NonNull String flowId, @NonNull Collection<L> eventListeners) {
        super(nextEvent, errorEvent, commandContext, carrier, eventListeners);
        this.flowId = flowId;
    }

    public void clearPendingCommands() {
        pendingCommands.clear();
    }

    public Optional<SwitchId> getPendingCommand(UUID key) {
        return Optional.ofNullable(pendingCommands.get(key));
    }

    public boolean hasPendingCommand(UUID key) {
        return pendingCommands.containsKey(key);
    }

    public void addPendingCommand(UUID key, SwitchId switchId) {
        pendingCommands.put(key, switchId);
    }

    public Optional<SwitchId> removePendingCommand(UUID key) {
        return Optional.ofNullable(pendingCommands.remove(key));
    }

    public void clearRetriedCommands() {
        retriedCommands.clear();
    }

    public int doRetryForCommand(UUID key) {
        int attempt = retriedCommands.getOrDefault(key, 0) + 1;
        retriedCommands.put(key, attempt);
        return attempt;
    }

    public void clearFailedCommands() {
        failedCommands.clear();
    }

    public void addFailedCommand(UUID key, SpeakerCommandResponse errorResponse) {
        failedCommands.put(key, errorResponse);
    }

    public void addSpeakerCommand(UUID key, BaseSpeakerCommandsRequest command) {
        speakerCommands.put(key, command);
    }

    public Optional<BaseSpeakerCommandsRequest> getSpeakerCommand(UUID key) {
        return Optional.ofNullable(speakerCommands.get(key));
    }

    public void clearSpeakerCommands() {
        speakerCommands.clear();
    }
}
