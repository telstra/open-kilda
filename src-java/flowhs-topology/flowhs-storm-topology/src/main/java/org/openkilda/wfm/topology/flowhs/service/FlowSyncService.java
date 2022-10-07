/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.messaging.command.flow.FlowSyncRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.service.common.SyncServiceBase;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FlowSyncService extends SyncServiceBase<FlowSyncFsm, FlowSyncFsm.Event> {
    private final FlowSyncFsm.Factory handlerFactory;

    public FlowSyncService(
            @NonNull FlowSyncCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesManager flowResourcesManager,
            @NonNull FlowPathOperationConfig flowPathOperationConfig) {
        super(FlowSyncFsm.EXECUTOR, carrier, persistenceManager);
        handlerFactory = new FlowSyncFsm.Factory(
                carrier, persistenceManager, flowResourcesManager, flowPathOperationConfig);
    }

    @Override
    protected void handleRequest(String requestKey, String targetId, CommandContext commandContext) {
        log.debug("Handling flow sync request with key {} and flow ID: {}", requestKey, targetId);
        super.handleRequest(requestKey, targetId, commandContext);
    }

    public void handleRequest(String requestKey, FlowSyncRequest request, CommandContext commandContext) {
        handleRequest(requestKey, request.getFlowId(), commandContext);
    }

    @Override
    protected Optional<FlowSyncFsm> lookupHandler(FlowPathReference reference) {
        return fsmRegister.getFsmByFlowId(reference.getFlowId());
    }

    @Override
    protected FlowSyncFsm newHandler(String flowId, CommandContext commandContext) {
        return handlerFactory.newInstance(flowId, commandContext);
    }
}
