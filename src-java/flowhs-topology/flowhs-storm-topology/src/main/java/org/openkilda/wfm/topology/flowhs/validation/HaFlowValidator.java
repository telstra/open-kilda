/* Copyright 2023 Telstra Open Source
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

import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.messaging.validation.ValidatorUtils;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import java.util.Collection;
import java.util.List;

/**
 * Checks whether y-flow can be created and has no conflicts with already created ones.
 */
public class HaFlowValidator {
    private final FlowValidator flowValidator;
    private final SwitchRepository switchRepository;

    public HaFlowValidator(PersistenceManager persistenceManager) {
        flowValidator = new FlowValidator(persistenceManager);
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    /**
     * Validates the specified flow id.
     *
     * @param id - ID of a flow, a y-flow, a ha-flow or a ha-subFlow.
     * @throws InvalidFlowException is thrown if a flow, a y-flow or ha-flow with specified ID exists.
     */
    public void validateFlowIdUniqueness(String id) throws InvalidFlowException {
        flowValidator.validateFlowIdUniqueness(id);
    }

    /**
     * Validates the specified y-flow request.
     *
     * @param request a request to be validated.
     * @throws InvalidFlowException is thrown if a violation is found.
     */
    public void validate(HaFlowRequest request) throws InvalidFlowException, UnavailableFlowEndpointException {
        if (request.getHaFlowId() == null) {
            throw new InvalidFlowException("The ha-flow id was not provided", ErrorType.DATA_INVALID);
        }

        if (request.getSharedEndpoint() == null) {
            throw new InvalidFlowException(
                    format("The ha-flow %s has no shared endpoint provided", request.getHaFlowId()),
                    ErrorType.DATA_INVALID);
        }

        checkSubFlows(request);
        checkOneSwitch(request);
        checkBandwidth(request);
        checkMaxLatency(request);
        validateSubFlows(HaFlowMapper.INSTANCE.toRequestedFlows(request));
    }

    private void checkSubFlows(HaFlowRequest request) throws InvalidFlowException {
        List<HaSubFlowDto> subFlows = request.getSubFlows();
        if (subFlows == null || subFlows.isEmpty()) {
            throw new InvalidFlowException(
                    format("The ha-flow %s has no sub flows provided", request.getHaFlowId()),
                    ErrorType.DATA_INVALID);
        }

        if (subFlows.size() != 2) {
            throw new InvalidFlowException(
                    format("The ha-flow %s must have 2 sub flows", request.getHaFlowId()),
                    ErrorType.DATA_INVALID);
        }

        for (HaSubFlowDto subFlow : subFlows) {
            if (subFlow.getFlowId() == null) {
                throw new InvalidFlowException(
                        format("The sub-flow of ha-flow %s has no sub-flow id provided", request.getHaFlowId()),
                        ErrorType.DATA_INVALID);
            }
            if (subFlow.getEndpoint() == null) {
                throw new InvalidFlowException(
                        format("The sub-flow %s of ha-flow %s has no endpoint provided",
                                subFlow.getFlowId(), request.getHaFlowId()), ErrorType.DATA_INVALID);
            }
        }

        if (subFlows.get(0).getEndpoint().equals(subFlows.get(1).getEndpoint())) {
            throw new InvalidFlowException(
                    format("The sub-flows %s and %s have endpoint conflict: %s / %s",
                            subFlows.get(0).getFlowId(), subFlows.get(1).getFlowId(),
                            subFlows.get(0).getEndpoint(), subFlows.get(1).getEndpoint()), ErrorType.DATA_INVALID);
        }
        if (subFlows.get(0).getEndpoint().getSwitchId().equals(subFlows.get(1).getEndpoint().getSwitchId())
                && subFlows.get(0).getEndpoint().getInnerVlanId() != subFlows.get(1).getEndpoint().getInnerVlanId()) {
            throw new InvalidFlowException(
                    format("To have ability to use double vlan tagging for both sub flow destination endpoints which "
                                    + "are placed on one switch %s you must set equal inner vlan for both endpoints. "
                                    + "Current inner vlans: %s and %s.",
                            subFlows.get(0).getEndpoint().getSwitchId(),
                            subFlows.get(0).getEndpoint().getInnerVlanId(),
                            subFlows.get(1).getEndpoint().getInnerVlanId()),
                    ErrorType.DATA_INVALID);
        }
    }

    private void checkOneSwitch(HaFlowRequest request) throws InvalidFlowException {
        for (HaSubFlowDto subFlow : request.getSubFlows()) {
            if (!subFlow.getEndpoint().getSwitchId().equals(request.getSharedEndpoint().getSwitchId())) {
                return;
            }
        }
        throw new InvalidFlowException(
                format("The ha-flow %s is one switch flow. At lease one of subflow endpoint switch id must differ "
                                + "from shared endpoint switch id %s",
                        request.getHaFlowId(), request.getSharedEndpoint().getSwitchId()),
                ErrorType.DATA_INVALID);
    }

    private void checkBandwidth(HaFlowRequest haFlowRequest)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        if (haFlowRequest.getMaximumBandwidth() < 0) {
            throw new InvalidFlowException(
                    format("The ha-flow %s has invalid bandwidth %d provided. Bandwidth cannot be less than 0 kbps.",
                            haFlowRequest.getHaFlowId(), haFlowRequest.getMaximumBandwidth()), ErrorType.DATA_INVALID);
        }

        Switch sharedSwitch = switchRepository.findById(haFlowRequest.getSharedEndpoint().getSwitchId())
                .orElseThrow(() -> new UnavailableFlowEndpointException(format("Endpoint switch not found %s",
                        haFlowRequest.getSharedEndpoint().getSwitchId())));

        boolean isNoviFlowSwitch = Switch.isNoviflowSwitch(sharedSwitch.getOfDescriptionSoftware());

        for (HaSubFlowDto subFlow : haFlowRequest.getSubFlows()) {
            Switch switchId = switchRepository.findById(subFlow.getEndpoint().getSwitchId())
                    .orElseThrow(() -> new UnavailableFlowEndpointException(format("Endpoint switch not found %s",
                            subFlow.getEndpoint().getSwitchId())));
            isNoviFlowSwitch |= Switch.isNoviflowSwitch(switchId.getOfDescriptionSoftware());
        }

        if (isNoviFlowSwitch && haFlowRequest.getMaximumBandwidth() != 0 && haFlowRequest.getMaximumBandwidth() < 64) {
            // Min rate that the NoviFlow switches allows is 64 kbps.
            throw new InvalidFlowException(
                    format("The ha-flow '%s' has invalid bandwidth %d provided. Bandwidth cannot be less than 64 kbps.",
                            haFlowRequest.getHaFlowId(), haFlowRequest.getMaximumBandwidth()), ErrorType.DATA_INVALID);
        }
    }

    private void checkMaxLatency(HaFlowRequest yFlowRequest) throws InvalidFlowException {
        ValidatorUtils.validateMaxLatencyAndLatencyTier(
                yFlowRequest.getMaxLatency(), yFlowRequest.getMaxLatencyTier2());
    }

    private void validateSubFlows(Collection<RequestedFlow> flows)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        for (RequestedFlow flow : flows) {
            flowValidator.validate(flow);
        }
    }
}
