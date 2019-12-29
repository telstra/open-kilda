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

import org.openkilda.adapter.FlowDestAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import com.google.common.annotations.VisibleForTesting;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Checks whether flow can be created and has no conflicts with already created ones.
 */
public class FlowValidator {

    private final FlowRepository flowRepository;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;

    public FlowValidator(PersistenceManager persistenceManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();

        this.flowRepository = repositoryFactory.createFlowRepository();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        this.islRepository = repositoryFactory.createIslRepository();
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @throws InvalidFlowException is thrown if a violation is found.
     */
    public void validate(RequestedFlow flow) throws InvalidFlowException, UnavailableFlowEndpointException {
        checkFlags(flow);
        checkBandwidth(flow);

        // TODO - fixme
        checkSwitchesSupportLldpAndArpIfNeeded(flow);

        final FlowEndpoint source = flow.getSourceEndpoint();
        final FlowEndpoint destination = flow.getDestinationEndpoint();
        checkOneSwitchFlowConflict(source, destination);

        checkSwitchesExistsAndActive(flow);
        if (StringUtils.isNotBlank(flow.getDiverseFlowId())) {
            checkDiverseFlow(flow);
        }

        for (EndpointDescriptor descriptor : new EndpointDescriptor[]{
                new EndpointDescriptor(source, "source"),
                new EndpointDescriptor(destination, "destination")}) {
            checkForMultiTableRequirement(descriptor);
            checkFlowForIslConflicts(descriptor);
            checkFlowForFlowConflicts(flow.getFlowId(), descriptor);
        }
    }

    @VisibleForTesting
    void checkFlags(RequestedFlow flow) throws InvalidFlowException  {
        if (flow.isPinned() && flow.isAllocateProtectedPath()) {
            throw new InvalidFlowException("Flow flags are not valid, unable to process pinned protected flow",
                    ErrorType.DATA_INVALID);
        }

        if (flow.isAllocateProtectedPath() && flow.getSrcSwitch().equals(flow.getDestSwitch())) {
            throw new InvalidFlowException("Couldn't setup protected path for one-switch flow",
                    ErrorType.PARAMETERS_INVALID);
        }
    }

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
     * Ensure vlans are not equal in the case when there is an attempt to create one-switch flow for a single port.
     */
    private void checkOneSwitchFlowConflict(FlowEndpoint source, FlowEndpoint destination) throws InvalidFlowException {
        if (source.equals(destination)) {
            throw new InvalidFlowException(
                    format("It is not allowed to create one-switch flow with \"equal\" endpoints (%s == %s)",
                           source, destination),
                    ErrorType.DATA_INVALID);
        }
    }

    /**
     * Ensure switches are exists.
     *
     * @param flow a flow to be validated.
     * @throws UnavailableFlowEndpointException if switch not found.
     */
    private void checkSwitchesExistsAndActive(RequestedFlow flow) throws UnavailableFlowEndpointException {
        final SwitchId sourceId = flow.getSrcSwitch();
        final SwitchId destinationId = flow.getDestSwitch();

        boolean sourceSwitchAvailable;
        boolean destinationSwitchAvailable;

        if (Objects.equals(sourceId, destinationId)) {
            Optional<Switch> sw = switchRepository.findById(sourceId);
            sourceSwitchAvailable = destinationSwitchAvailable = sw.map(Switch::isActive)
                    .orElse(false);
        } else {
            sourceSwitchAvailable = switchRepository.findById(sourceId)
                    .map(Switch::isActive)
                    .orElse(false);
            destinationSwitchAvailable = switchRepository.findById(destinationId)
                    .map(Switch::isActive)
                    .orElse(false);
        }

        if (!sourceSwitchAvailable && !destinationSwitchAvailable) {
            throw new UnavailableFlowEndpointException(
                    String.format("Source switch %s and Destination switch %s are not connected to the controller",
                            sourceId, destinationId));
        } else if (!sourceSwitchAvailable) {
            throw new UnavailableFlowEndpointException(
                    String.format("Source switch %s is not connected to the controller", sourceId));
        } else if (!destinationSwitchAvailable) {
            throw new UnavailableFlowEndpointException(
                    String.format("Destination switch %s is not connected to the controller", destinationId));
        }
    }

    private void checkDiverseFlow(RequestedFlow targetFlow) throws InvalidFlowException {
        if (targetFlow.getSrcSwitch().equals(targetFlow.getDestSwitch())) {
            throw new InvalidFlowException("Couldn't add one-switch flow into diverse group",
                    ErrorType.PARAMETERS_INVALID);
        }

        Flow diverseFlow = flowRepository.findById(targetFlow.getDiverseFlowId())
                .orElseThrow(() ->
                        new InvalidFlowException(format("Failed to find diverse flow id %s",
                                targetFlow.getDiverseFlowId()), ErrorType.PARAMETERS_INVALID));

        if (diverseFlow.isOneSwitchFlow()) {
            throw new InvalidFlowException("Couldn't create diverse group with one-switch flow",
                    ErrorType.PARAMETERS_INVALID);
        }
    }

    /**
     * Ensure switches support LLDP/ARP.
     *
     * @param requestedFlow a flow to be validated.
     */
    // FIXME: switch to per endpoint based stategy (same as other enpoint related checks)
    @VisibleForTesting
    void checkSwitchesSupportLldpAndArpIfNeeded(RequestedFlow requestedFlow) throws InvalidFlowException {
        SwitchId sourceId = requestedFlow.getSrcSwitch();
        SwitchId destinationId = requestedFlow.getDestSwitch();

        List<String> errorMessages = new ArrayList<>();

        if (requestedFlow.getDetectConnectedDevices().isSrcLldp()
                || requestedFlow.getDetectConnectedDevices().isSrcArp()) {
            validateMultiTableProperty(sourceId, errorMessages);
        }

        if (requestedFlow.getDetectConnectedDevices().isDstLldp()
                || requestedFlow.getDetectConnectedDevices().isDstArp()) {
            validateMultiTableProperty(destinationId, errorMessages);
        }

        if (!errorMessages.isEmpty()) {
            throw new InvalidFlowException(String.join(" ", errorMessages), ErrorType.DATA_INVALID);
        }
    }

    private void validateMultiTableProperty(SwitchId switchId, List<String> errorMessages) {
        Optional<SwitchProperties> switchProperties = switchPropertiesRepository.findBySwitchId(switchId);
        if (!switchProperties.isPresent()) {
            errorMessages.add(String.format("Couldn't get switch properties for switch %s.", switchId));
        } else {
            if (!switchProperties.get().isMultiTable()) {
                errorMessages.add(String.format("Catching of LLDP/ARP packets supported only on switches with "
                                + "enabled 'multiTable' switch feature. This feature is disabled on switch %s.",
                        switchId));
            }
        }
    }

    private void checkForMultiTableRequirement(EndpointDescriptor descriptor) throws InvalidFlowException {
        FlowEndpoint endpoint = descriptor.getEndpoint();
        if (endpoint.getVlanStack().size() < 2) {
            return;
        }

        SwitchProperties switchProperties = switchPropertiesRepository.findBySwitchId(
                endpoint.getSwitchId())
                .orElseGet(() -> SwitchProperties.builder().build());
        if (! switchProperties.isMultiTable()) {
            final String errorMessage = format(
                    "Flow's %s endpoint is double VLAN tagged, switch %s is not capable to support such endpoint "
                            + "encapsulation.",
                    descriptor.getName(), endpoint.getSwitchId());
            throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }
    }

    private void checkFlowForIslConflicts(EndpointDescriptor descriptor) throws InvalidFlowException {
        FlowEndpoint endpoint = descriptor.getEndpoint();
        if (! islRepository.findByEndpoint(endpoint.getSwitchId(), endpoint.getPortNumber()).isEmpty()) {
            String errorMessage = format(
                    "The port %d on the switch '%s' is occupied by an ISL (conflict with %s endpoint).",
                    endpoint.getPortNumber(), endpoint.getSwitchId(), descriptor.getName());
            throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }
    }

    /**
     * Checks a flow for endpoints' conflicts.
     *
     * @throws InvalidFlowException is thrown in a case when flow endpoints conflict with existing flows.
     */
    private void checkFlowForFlowConflicts(String flowId, EndpointDescriptor descriptor) throws InvalidFlowException {
        final FlowEndpoint endpoint = descriptor.getEndpoint();

        for (Flow entry : flowRepository.findByEndpoint(endpoint.getSwitchId(), endpoint.getPortNumber())) {
            if (flowId.equals(entry.getFlowId())) {
                continue;
            }

            FlowEndpoint source = new FlowSourceAdapter(entry).getEndpoint();
            FlowEndpoint destination = new FlowDestAdapter(entry).getEndpoint();

            FlowEndpoint conflict = null;
            if (endpoint.detectConflict(source)) {
                conflict = source;
            } else if (endpoint.detectConflict(destination)) {
                conflict = destination;
            }

            if (conflict != null) {
                String errorMessage = format(
                        "Requested flow '%s' %s endpoint %s conflicts with existing flow '%s' endpoint %s",
                        flowId, descriptor.getName(), endpoint, entry.getFlowId(), conflict);
                throw new InvalidFlowException(errorMessage, ErrorType.ALREADY_EXISTS);
            }
        }
    }

    @Value
    private static class EndpointDescriptor {
        private final FlowEndpoint endpoint;
        private final String name;
    }
}
