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
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import com.google.common.annotations.VisibleForTesting;

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
     * @throws FlowValidationException is thrown if a violation is found.
     */
    public void validate(Flow flow) throws FlowValidationException, SwitchValidationException {
        checkBandwidth(flow);
        checkFlowForEndpointConflicts(flow);
        checkOneSwitchFlowHasNoConflicts(flow);
        checkSwitchesExists(flow);
    }

    @VisibleForTesting
    void checkBandwidth(Flow flow) throws FlowValidationException {
        if (flow.getBandwidth() < 0) {
            throw new FlowValidationException(
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
     * @throws FlowValidationException is thrown in a case when flow endpoints conflict with existing flows.
     */
    @VisibleForTesting
    void checkFlowForEndpointConflicts(Flow flow) throws FlowValidationException {
        checkEndpoint(flow.getFlowId(), flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getSrcVlan());
        checkEndpoint(flow.getFlowId(), flow.getDestSwitch().getSwitchId(), flow.getDestPort(), flow.getDestVlan());
    }

    private void checkEndpoint(String flowId, SwitchId dpid, int portNo, int vlanId) throws FlowValidationException {
        Optional<String> conflictedFlowId = flowRepository.findByEndpoint(dpid, portNo)
                .stream()
                .filter(flow -> !flowId.equals(flow.getFlowId()))
                .filter(flow -> (flow.getSrcSwitch().getSwitchId().equals(dpid)
                        && flow.getSrcPort() == portNo
                        && (flow.getSrcVlan() == vlanId || flow.getSrcVlan() == 0 || vlanId == 0))
                        || (flow.getDestSwitch().getSwitchId().equals(dpid)
                        && flow.getDestPort() == portNo
                        && (flow.getDestVlan() == vlanId || flow.getDestVlan() == 0
                        || vlanId == 0)))
                .map(Flow::getFlowId)
                .findAny();

        if (conflictedFlowId.isPresent()) {
            throw new FlowValidationException(
                    format("The port %d on the switch '%s' is already occupied by the flow '%s'.",
                            portNo, dpid, flowId),
                    ErrorType.ALREADY_EXISTS);
        }
    }

    /**
     * Ensure switches are exists.
     *
     * @param flow a flow to be validated.
     * @throws SwitchValidationException if switch not found.
     */
    @VisibleForTesting
    void checkSwitchesExists(Flow flow) throws SwitchValidationException {
        final SwitchId sourceId = flow.getSrcSwitch().getSwitchId();
        final SwitchId destinationId = flow.getDestSwitch().getSwitchId();

        boolean source;
        boolean destination;

        if (Objects.equals(sourceId, destinationId)) {
            source = destination = switchRepository.exists(sourceId);
        } else {
            source = switchRepository.exists(sourceId);
            destination = switchRepository.exists(destinationId);
        }

        if (!source && !destination) {
            throw new SwitchValidationException(
                    String.format("Source switch %s and Destination switch %s are not connected to the controller",
                            sourceId, destinationId));
        } else if (!source) {
            throw new SwitchValidationException(
                    String.format("Source switch %s is not connected to the controller", sourceId));
        } else if (!destination) {
            throw new SwitchValidationException(
                    String.format("Destination switch %s is not connected to the controller", destinationId));
        }
    }

    /**
     * Ensure vlans are not equal in the case when there is an attempt to create one-switch flow for a single port.
     */
    @VisibleForTesting
    void checkOneSwitchFlowHasNoConflicts(Flow requestedFlow) throws SwitchValidationException {
        if (requestedFlow.getSrcSwitch().equals(requestedFlow.getDestSwitch())
                && requestedFlow.getSrcPort() == requestedFlow.getDestPort()
                && requestedFlow.getSrcVlan() == requestedFlow.getDestVlan()) {

            throw new SwitchValidationException(
                    "It is not allowed to create one-switch flow for the same ports and vlans");
        }
    }
}
