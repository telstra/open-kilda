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

package org.openkilda.wfm.topology.flowhs.service.path;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.path.FlowPathInstallFsm;
import org.openkilda.wfm.topology.flowhs.fsm.path.FlowPathOperation;
import org.openkilda.wfm.topology.flowhs.fsm.path.FlowPathRemoveFsm;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResult;
import org.openkilda.wfm.topology.flowhs.service.common.FlowHsService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class FlowPathService extends FlowHsService {
    private final Map<String, FlowPathOperation> registry = new HashMap<>();

    private final FlowPathCarrier carrier;
    private final FlowPathInstallFsm.Factory installOperationFactory;
    private final FlowPathRemoveFsm.Factory removeOperationFactory;

    public FlowPathService(@NonNull FlowPathCarrier carrier) {
        this.carrier = carrier;
        installOperationFactory = new FlowPathInstallFsm.Factory(carrier);
        removeOperationFactory = new FlowPathRemoveFsm.Factory(carrier);
    }

    /**
     * Launch flow path install operation.
     */
    public void installPath(
            @NonNull FlowPathRequest request, @NonNull String requestKey, @NonNull FlowPathOperationConfig config,
            @NonNull CommandContext commandContext)
            throws DuplicateKeyException {
        handleRequest(
                "install", request.getReference(), requestKey,
                () -> installOperationFactory.newInstance(config, request, commandContext));
    }

    /**
     * Launch flow path remove operation.
     */
    public void removePath(
            @NonNull FlowPathRequest request, @NonNull String requestKey, @NonNull FlowPathOperationConfig config,
            @NonNull CommandContext commandContext)
            throws DuplicateKeyException {
        handleRequest(
                "remove", request.getReference(), requestKey,
                () -> removeOperationFactory.newInstance(config, request, commandContext));
    }

    /**
     * Cancel possible running path operation.
     */
    public void cancelOperation(@NonNull String requestKey) throws UnknownKeyException {
        handleEvent(requestKey, FlowPathOperation::handleCancel);
    }

    public void handleSpeakerResponse(@NonNull String requestKey, @NonNull SpeakerFlowSegmentResponse response)
            throws UnknownKeyException {
        handleEvent(requestKey, handler -> handler.handleSpeakerResponse(response));
    }

    private void handleRequest(
            String operationName, FlowPathReference reference, String requestKey,
            Supplier<FlowPathOperation> handlerSupplier) throws DuplicateKeyException {
        log.debug("Handling {} {} request (key={})", reference, operationName, requestKey);

        ensureNoOperationCollisions(requestKey, operationName);
        FlowPathOperation handler = handlerSupplier.get();
        registry.put(requestKey, handler);
        handler.getResultFuture()
                .thenAccept(result -> onComplete(operationName, requestKey, handler, result));
    }

    private void handleEvent(String requestKey, Consumer<FlowPathOperation> action) throws UnknownKeyException {
        FlowPathOperation handler = registry.get(requestKey);
        if (handler == null) {
            throw new UnknownKeyException(requestKey);
        }
        action.accept(handler);
    }

    private void onComplete(
            String operationName, String requestKey, FlowPathOperation operation, FlowPathResult result) {
        log.debug(
                "Flow path {} operation with reference {} completed with result {}",
                operationName, operation.getPathReference(), operation.getResultCode());

        registry.remove(requestKey);
        carrier.processFlowPathOperationResults(result);
    }

    private void ensureNoOperationCollisions(String requestKey, String operationName) throws DuplicateKeyException {
        if (registry.containsKey(requestKey)) {
            throw new DuplicateKeyException(
                    requestKey, String.format(
                            "Flow path %s requests collision path reference %s", operationName, requestKey));
        }
    }
}
