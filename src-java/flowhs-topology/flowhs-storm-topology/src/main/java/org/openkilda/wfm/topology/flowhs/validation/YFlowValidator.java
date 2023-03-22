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
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.messaging.validation.ValidatorUtils;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import java.util.Collection;
import java.util.List;

/**
 * Checks whether y-flow can be created and has no conflicts with already created ones.
 */
public class YFlowValidator {
    private final FlowValidator flowValidator;
    private final SwitchRepository switchRepository;

    public YFlowValidator(PersistenceManager persistenceManager) {
        flowValidator = new FlowValidator(persistenceManager);
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    /**
     * Validates the specified y-flow request.
     *
     * @param request a request to be validated.
     * @throws InvalidFlowException is thrown if a violation is found.
     */
    public void validate(YFlowRequest request) throws InvalidFlowException, UnavailableFlowEndpointException {
        if (request.getYFlowId() == null) {
            throw new InvalidFlowException("The y-flow id was not provided", ErrorType.DATA_INVALID);
        }

        if (request.getSharedEndpoint() == null) {
            throw new InvalidFlowException(
                    format("The y-flow %s has no shared endpoint provided", request.getYFlowId()),
                    ErrorType.DATA_INVALID);
        }

        checkSubFlows(request);
        checkSubFlowsHaveNoConflict(request.getSubFlows());
        checkBandwidth(request);
        checkMaxLatency(request);

        validateSubFlows(YFlowRequestMapper.INSTANCE.toRequestedFlows(request));
    }

    private void checkSubFlows(YFlowRequest request) throws InvalidFlowException {
        List<SubFlowDto> subFlows = request.getSubFlows();
        if (subFlows == null || subFlows.isEmpty()) {
            throw new InvalidFlowException(
                    format("The y-flow %s has no sub flows provided", request.getYFlowId()),
                    ErrorType.DATA_INVALID);
        }

        if (subFlows.size() < 2) {
            throw new InvalidFlowException(
                    format("The y-flow %s must have at least 2 sub flows", request.getYFlowId()),
                    ErrorType.DATA_INVALID);
        }

        for (SubFlowDto subFlow : subFlows) {
            if (subFlow.getFlowId() == null) {
                throw new InvalidFlowException(
                        format("The sub-flow of y-flow %s has no sub-flow id provided", request.getYFlowId()),
                        ErrorType.DATA_INVALID);
            }
            if (subFlow.getSharedEndpoint() == null) {
                throw new InvalidFlowException(
                        format("The sub-flow %s of y-flow %s has no shared endpoint provided",
                                subFlow.getFlowId(), request.getYFlowId()), ErrorType.DATA_INVALID);
            }
            if (subFlow.getEndpoint() == null) {
                throw new InvalidFlowException(
                        format("The sub-flow %s of y-flow %s has no endpoint provided",
                                subFlow.getFlowId(), request.getYFlowId()), ErrorType.DATA_INVALID);
            }
        }
    }

    private void checkSubFlowsHaveNoConflict(List<SubFlowDto> subFlows) throws InvalidFlowException {
        for (SubFlowDto subFlow : subFlows) {
            for (SubFlowDto another : subFlows) {
                if (subFlow == another) {
                    continue;
                }
                if (subFlow.getSharedEndpoint().equals(another.getSharedEndpoint())) {
                    throw new InvalidFlowException(
                            format("The sub-flows %s and %s have shared endpoint conflict: %s / %s",
                                    subFlow.getFlowId(), another.getFlowId(), subFlow.getSharedEndpoint(),
                                    another.getSharedEndpoint()), ErrorType.DATA_INVALID);
                }
                if (subFlow.getEndpoint().equals(another.getEndpoint())) {
                    throw new InvalidFlowException(
                            format("The sub-flows %s and %s have endpoint conflict: %s / %s",
                                    subFlow.getFlowId(), another.getFlowId(), subFlow.getEndpoint(),
                                    another.getEndpoint()), ErrorType.DATA_INVALID);
                }
            }
        }
    }

    private void checkBandwidth(YFlowRequest yFlowRequest)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        if (yFlowRequest.getMaximumBandwidth() < 0) {
            throw new InvalidFlowException(
                    format("The y-flow %s has invalid bandwidth %d provided. Bandwidth cannot be less than 0 kbps.",
                            yFlowRequest.getYFlowId(),
                            yFlowRequest.getMaximumBandwidth()),
                    ErrorType.DATA_INVALID);
        }

        Switch sharedSwitch = switchRepository.findById(yFlowRequest.getSharedEndpoint().getSwitchId())
                .orElseThrow(() -> new UnavailableFlowEndpointException(format("Endpoint switch not found %s",
                        yFlowRequest.getSharedEndpoint().getSwitchId())));

        boolean isNoviFlowSwitch = Switch.isNoviflowSwitch(sharedSwitch.getOfDescriptionSoftware());

        for (SubFlowDto subFlow : yFlowRequest.getSubFlows()) {
            Switch switchId = switchRepository.findById(subFlow.getEndpoint().getSwitchId())
                    .orElseThrow(() -> new UnavailableFlowEndpointException(format("Endpoint switch not found %s",
                            subFlow.getEndpoint().getSwitchId())));
            isNoviFlowSwitch |= Switch.isNoviflowSwitch(switchId.getOfDescriptionSoftware());
        }

        if (isNoviFlowSwitch && yFlowRequest.getMaximumBandwidth() != 0 && yFlowRequest.getMaximumBandwidth() < 64) {
            // Min rate that the NoviFlow switches allows is 64 kbps.
            throw new InvalidFlowException(
                    format("The flow '%s' has invalid bandwidth %d provided. Bandwidth cannot be less than 64 kbps.",
                            yFlowRequest.getYFlowId(),
                            yFlowRequest.getMaximumBandwidth()),
                    ErrorType.DATA_INVALID);
        }
    }

    private void checkMaxLatency(YFlowRequest yFlowRequest) throws InvalidFlowException {
        ValidatorUtils.validateMaxLatencyAndLatencyTier(yFlowRequest.getMaxLatency(),
                yFlowRequest.getMaxLatencyTier2());
    }
    
    private void validateSubFlows(Collection<RequestedFlow> flows)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        for (RequestedFlow flow : flows) {
            flowValidator.validate(flow);
        }
    }
}
