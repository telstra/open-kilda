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
import org.openkilda.messaging.info.stats.YFlowStatsInfoFactory;
import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.InsufficientDataException;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.ProcessingEventListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Getter
public abstract class YFlowProcessingFsm<T extends StateMachine<T, S, E, C>, S, E, C,
        R extends FlowGenericCarrier, L extends ProcessingEventListener>
        extends FlowProcessingWithHistorySupportFsm<T, S, E, C, R, L> {
    private final String yFlowId;

    private final Map<UUID, SwitchId> pendingCommands = new HashMap<>();
    private final Map<UUID, Integer> retriedCommands = new HashMap<>();
    private final Map<UUID, SpeakerResponse> failedCommands = new HashMap<>();
    private final Map<UUID, SpeakerResponse> failedValidationResponses = new HashMap<>();

    @Setter
    private FlowStatus originalYFlowStatus;
    @Setter
    private YFlowResources newResources;

    protected YFlowProcessingFsm(E nextEvent, E errorEvent,
                                 @NonNull CommandContext commandContext, @NonNull R carrier, @NonNull String yFlowId,
                                 @NonNull Collection<L> eventListeners) {
        super(nextEvent, errorEvent, commandContext, carrier, eventListeners);
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

    public void sendAddOrUpdateStatsNotification(YFlowResources resources) {
        try {
            getCarrier().sendStatsNotification(newYFlowStatsInfoFactory(resources).produceAddUpdateNotification());
        } catch (InsufficientDataException e) {
            log.error(
                    "Do not notify stats about new y-flows resources allocation - resources data is incomplete {}",
                    e.getMessage());
        }
    }

    public void sendRemoveStatsNotification(YFlowResources resources) {
        try {
            getCarrier().sendStatsNotification(newYFlowStatsInfoFactory(resources).produceRemoveYFlowStatsInfo());
        } catch (InsufficientDataException e) {
            log.info("Do not notify stats about y-flows resources release - resources data is incomplete {}",
                    e.getMessage());  // resource can be incomplete
        }
    }

    private YFlowStatsInfoFactory newYFlowStatsInfoFactory(YFlowResources resources) throws InsufficientDataException {
        YFlowResources.EndpointResources sharedEndpoint = resources.getSharedEndpointResources();
        YFlowResources.EndpointResources yPoint = resources.getMainPathYPointResources();

        String missing;
        if (sharedEndpoint == null && yPoint == null) {
            missing = "shared and y-point endpoints";
        } else if (sharedEndpoint == null) {
            missing = "shared endpoint";
        } else if (yPoint == null) {
            missing = "y-point endpoint";
        } else {
            missing = null;
        }
        if (missing != null) {
            throw new InsufficientDataException(String.format("%s data are empty (is null)", missing));
        }

        return new YFlowStatsInfoFactory(
                getYFlowId(),
                new YFlowEndpointResources(sharedEndpoint.getEndpoint(), sharedEndpoint.getMeterId()),
                new YFlowEndpointResources(yPoint.getEndpoint(), yPoint.getMeterId()));
    }
}
