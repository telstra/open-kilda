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

package org.openkilda.wfm.topology.flowhs.validation;

import static java.lang.String.format;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import java.util.Collection;
import java.util.List;

/**
 * Checks whether y-flow can be created and has no conflicts with already created ones.
 */
public class YFlowValidator {
    private final FlowValidator flowValidator;
    private final YFlowRepository yFlowRepository;

    public YFlowValidator(PersistenceManager persistenceManager) {
        flowValidator = new FlowValidator(persistenceManager);
        yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
    }

    /**
     * Validates the specified y-flow request.
     *
     * @param request a request to be validated.
     * @throws InvalidFlowException is thrown if a violation is found.
     */
    public void validate(YFlowRequest request) throws InvalidFlowException, UnavailableFlowEndpointException {
        if (request.getSharedEndpoint() == null) {
            throw new InvalidFlowException(
                    format("The y-flow '%s' has no shared endpoint provided", request.getYFlowId()),
                    ErrorType.DATA_INVALID);
        }

        checkSubFlows(request);
        checkNoOneSwitchFlow(request);
        checkBandwidth(request);

        validateSubFlows(YFlowRequestMapper.INSTANCE.toRequestedFlows(request));
    }

    private void checkSubFlows(YFlowRequest request) throws InvalidFlowException {
        List<SubFlowDto> subFlows = request.getSubFlows();
        if (subFlows == null || subFlows.isEmpty()) {
            throw new InvalidFlowException(
                    format("The y-flow '%s' has no sub flows provided", request.getYFlowId()),
                    ErrorType.DATA_INVALID);
        }

        if (subFlows.size() < 2) {
            throw new InvalidFlowException(
                    format("The y-flow '%s' must have at least 2 sub flows", request.getYFlowId()),
                    ErrorType.DATA_INVALID);
        }

        for (SubFlowDto subFlow : subFlows) {
            if (subFlow.getSharedEndpoint() == null) {
                throw new InvalidFlowException(
                        format("The sub-flow of y-flow '%s' has no shared endpoint provided", request.getYFlowId()),
                        ErrorType.DATA_INVALID);
            }
            if (subFlow.getEndpoint() == null) {
                throw new InvalidFlowException(
                        format("The sub-flow of y-flow '%s' has no endpoint provided", request.getYFlowId()),
                        ErrorType.DATA_INVALID);
            }
        }
    }

    private void checkNoOneSwitchFlow(YFlowRequest yFlowRequest) throws InvalidFlowException {
        for (SubFlowDto subFlow : yFlowRequest.getSubFlows()) {
            if (yFlowRequest.getSharedEndpoint().getSwitchId().equals(subFlow.getEndpoint().getSwitchId())) {
                throw new InvalidFlowException(
                        "It is not allowed to create one-switch y-flow", ErrorType.DATA_INVALID);
            }
        }
    }

    private void checkBandwidth(YFlowRequest yFlowRequest) throws InvalidFlowException {
        if (yFlowRequest.getMaximumBandwidth() < 0) {
            throw new InvalidFlowException(
                    format("The y-flow '%s' has invalid bandwidth %d provided.",
                            yFlowRequest.getYFlowId(),
                            yFlowRequest.getMaximumBandwidth()),
                    ErrorType.DATA_INVALID);
        }
    }

    private void validateSubFlows(Collection<RequestedFlow> flows)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        for (RequestedFlow flow : flows) {
            flowValidator.validate(flow);
        }
    }
}
