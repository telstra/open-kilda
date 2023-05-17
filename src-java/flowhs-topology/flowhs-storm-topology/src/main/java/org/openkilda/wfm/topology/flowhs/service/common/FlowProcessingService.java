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

package org.openkilda.wfm.topology.flowhs.service.common;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.FsmExecutor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

@Slf4j
public abstract class FlowProcessingService<T extends AbstractStateMachine<T, ?, E, C>, E, C,
        R extends NorthboundResponseCarrier & LifecycleEventCarrier, F extends FsmRegister<String, T>,
        L extends ProcessingEventListener> extends FsmBasedProcessingService<T, E, C, F, L> {
    protected final R carrier;
    protected final FlowRepository flowRepository;
    protected final YFlowRepository yFlowRepository;
    protected final HaFlowRepository haFlowRepository;

    protected FlowProcessingService(@NonNull F fsmRegister,
                                    @NonNull FsmExecutor<T, ?, E, C> fsmExecutor,
                                    @NonNull R carrier,
                                    @NonNull PersistenceManager persistenceManager) {
        super(fsmRegister, fsmExecutor);
        this.carrier = carrier;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        haFlowRepository = repositoryFactory.createHaFlowRepository();
    }

    /**
     * Sends error response to northbound component.
     */
    protected void sendErrorResponseToNorthbound(ErrorType errorType, String errorMessage, String errorDescription,
                                                 CommandContext commandContext) {
        ErrorData errorData = new ErrorData(errorType, errorMessage, errorDescription);
        carrier.sendNorthboundResponse(new ErrorMessage(errorData, commandContext.getCreateTime(),
                commandContext.getCorrelationId()));
    }

    protected void sendForbiddenSubFlowOperationToNorthbound(String flowId, CommandContext commandContext) {
        sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not modify flow",
                format("%s is a sub-flow of a y-flow. Operations on sub-flows are forbidden.", flowId),
                commandContext);
    }

    protected void cancelProcessing(String key) {
        carrier.cancelTimeoutCallback(key);
        if (!isActive() && !fsmRegister.hasAnyRegisteredFsm()) {
            carrier.sendInactive();
        }
    }
}
