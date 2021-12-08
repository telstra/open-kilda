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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
public abstract class YFlowProcessingFsm<T extends NbTrackableFsm<T, S, E, C, R>, S, E, C,
        R extends FlowGenericCarrier, L extends FlowProcessingEventListener>
        extends FlowProcessingWithEventSupportFsm<T, S, E, C, R, L> {

    private final String yFlowId;

    private final Map<UUID, SwitchId> pendingCommands = new HashMap<>();
    private final Map<UUID, Integer> retriedCommands = new HashMap<>();
    private final Map<UUID, SpeakerResponse> failedCommands = new HashMap<>();
    private final Map<UUID, SpeakerResponse> failedValidationResponses = new HashMap<>();

    @Setter
    private FlowStatus originalYFlowStatus;
    @Setter
    private YFlowResources newResources;

    public YFlowProcessingFsm(CommandContext commandContext, @NonNull R carrier, @NonNull String yFlowId,
                              @NonNull Collection<L> eventListeners) {
        super(commandContext, carrier, true, eventListeners);

        this.yFlowId = yFlowId;
    }

    @Override
    public final String getFlowId() {
        return getYFlowId();
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

    public void addFailedCommand(UUID key, SpeakerResponse errorResponse) {
        failedCommands.put(key, errorResponse);
    }

    public void clearPendingAndRetriedAndFailedCommands() {
        clearPendingCommands();
        clearRetriedCommands();
        clearFailedCommands();
    }
}
