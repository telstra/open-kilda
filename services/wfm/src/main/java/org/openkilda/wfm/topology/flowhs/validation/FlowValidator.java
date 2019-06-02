/* Copyright 2018 Telstra Open Source
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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Checks whether flow can be created and has no conflicts with already created ones.
 */
public class FlowValidator {

    private final FlowRepository flowRepository;
    private final SwitchRepository switchRepository;

    public FlowValidator(FlowRepository flowRepository, SwitchRepository switchRepository) {
        this.flowRepository = flowRepository;
        this.switchRepository = switchRepository;
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @throws InvalidFlowException is thrown if a violation is found.
     */
    public void validate(RequestedFlow flow) throws InvalidFlowException, UnavailableFlowEndpointException {
        checkBandwidth(flow);
        checkFlowForEndpointConflicts(flow);
        checkOneSwitchFlowHasNoConflicts(flow);
        checkSwitchesExists(flow);
    }

    @VisibleForTesting
    void checkBandwidth(RequestedFlow flow) throws InvalidFlowException {
        if (flow.getBandwidth() < 0) {
            throw new InvalidFlowException(
                    format("The flow '%s' has invalid bandwidth %d provided.",
                            flow.getFlowId(),
                            flow.getBandwidth()),
                    ErrorType.DATA_INVALID);
        }
    }

    /**
     * Checks a flow for endpoints' conflicts.
     *
     * @param flow a flow to be validated.
     * @throws InvalidFlowException is thrown in a case when flow endpoints conflict with existing flows.
     */
    @VisibleForTesting
    void checkFlowForEndpointConflicts(RequestedFlow flow) throws InvalidFlowException {
        checkEndpoint(flow.getFlowId(), flow.getSrcSwitch(), flow.getSrcPort(), flow.getSrcVlan(), true);
        checkEndpoint(flow.getFlowId(), flow.getDestSwitch(), flow.getDestPort(), flow.getDestVlan(), false);
    }

    private void checkEndpoint(String flowId, SwitchId switchId, int portNo, int vlanId, boolean isSource)
            throws InvalidFlowException {
        Collection<Flow> conflicts = flowRepository.findByEndpoint(switchId, portNo);
        Optional<Flow> conflictOnSource = conflicts.stream()
                .filter(flow -> !flowId.equals(flow.getFlowId()))
                .filter(flow -> (flow.getSrcSwitch().getSwitchId().equals(switchId)
                        && flow.getSrcPort() == portNo
                        && (flow.getSrcVlan() == vlanId
                        || flow.getSrcVlan() == 0 || vlanId == 0)))
                .findAny();
        if (conflictOnSource.isPresent()) {
            Flow existingFlow = conflictOnSource.get();
            String errorMessage = format("Requested flow '%s' conflicts with existing flow '%s'. "
                            + "Details: "
                            + "requested flow '%s' "
                            + (isSource ? "source" : "destination")
                            + ": switch=%s port=%d vlan=%d, "
                            + "existing flow '%s' source: switch=%s port=%d vlan=%d",
                    flowId, existingFlow.getFlowId(),
                    flowId, switchId, portNo, vlanId,
                    existingFlow.getFlowId(),
                    existingFlow.getSrcSwitch().getSwitchId().toString(),
                    existingFlow.getSrcPort(), existingFlow.getSrcVlan());
            throw new InvalidFlowException(errorMessage, ErrorType.ALREADY_EXISTS);
        }

        Optional<Flow> conflictOnDest = conflicts.stream()
                .filter(flow -> !flowId.equals(flow.getFlowId()))
                .filter(flow -> flow.getDestSwitch().getSwitchId().equals(switchId)
                        && flow.getDestPort() == portNo
                        && (flow.getDestVlan() == vlanId
                        || flow.getDestVlan() == 0 || vlanId == 0))
                .findAny();
        if (conflictOnDest.isPresent()) {
            Flow existingFlow = conflictOnDest.get();
            String errorMessage = format("Requested flow '%s' conflicts with existing flow '%s'. "
                            + "Details: "
                            + "requested flow '%s' "
                            + (isSource ? "source" : "destination")
                            + ": switch=%s port=%d vlan=%d, "
                            + "existing flow '%s' destination: switch=%s port=%d vlan=%d",
                    flowId, existingFlow.getFlowId(),
                    flowId, switchId.toString(), portNo, vlanId,
                    existingFlow.getFlowId(),
                    existingFlow.getDestSwitch().getSwitchId().toString(),
                    existingFlow.getDestPort(), existingFlow.getDestVlan());
            throw new InvalidFlowException(errorMessage, ErrorType.ALREADY_EXISTS);
        }
    }

    /**
     * Ensure switches are exists.
     *
     * @param flow a flow to be validated.
     * @throws UnavailableFlowEndpointException if switch not found.
     */
    @VisibleForTesting
    void checkSwitchesExists(RequestedFlow flow) throws UnavailableFlowEndpointException {
        final SwitchId sourceId = flow.getSrcSwitch();
        final SwitchId destinationId = flow.getDestSwitch();

        boolean source;
        boolean destination;

        if (Objects.equals(sourceId, destinationId)) {
            source = destination = switchRepository.exists(sourceId);
        } else {
            source = switchRepository.exists(sourceId);
            destination = switchRepository.exists(destinationId);
        }

        if (!source && !destination) {
            throw new UnavailableFlowEndpointException(
                    String.format("Source switch %s and Destination switch %s are not connected to the controller",
                            sourceId, destinationId));
        } else if (!source) {
            throw new UnavailableFlowEndpointException(
                    String.format("Source switch %s is not connected to the controller", sourceId));
        } else if (!destination) {
            throw new UnavailableFlowEndpointException(
                    String.format("Destination switch %s is not connected to the controller", destinationId));
        }
    }

    /**
     * Ensure vlans are not equal in the case when there is an attempt to create one-switch flow for a single port.
     */
    @VisibleForTesting
    void checkOneSwitchFlowHasNoConflicts(RequestedFlow requestedFlow) throws InvalidFlowException {
        if (requestedFlow.getSrcSwitch().equals(requestedFlow.getDestSwitch())
                && requestedFlow.getSrcPort() == requestedFlow.getDestPort()
                && requestedFlow.getSrcVlan() == requestedFlow.getDestVlan()) {

            throw new InvalidFlowException(
                    "It is not allowed to create one-switch flow for the same ports and vlans", ErrorType.DATA_INVALID);
        }
    }
}
