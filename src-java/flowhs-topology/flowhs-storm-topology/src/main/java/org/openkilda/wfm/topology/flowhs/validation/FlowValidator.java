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

import org.openkilda.adapter.FlowDestAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlowMirrorPoint;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks whether flow can be created and has no conflicts with already created ones.
 */
public class FlowValidator {

    private final FlowRepository flowRepository;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final FlowMirrorPathRepository flowMirrorPathRepository;
    private final PhysicalPortRepository physicalPortRepository;

    public FlowValidator(PersistenceManager persistenceManager) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
        this.flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
        this.physicalPortRepository = persistenceManager.getRepositoryFactory().createPhysicalPortRepository();
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @throws InvalidFlowException is thrown if a violation is found.
     */
    public void validate(RequestedFlow flow) throws InvalidFlowException, UnavailableFlowEndpointException {
        validate(flow, new HashSet<>());
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @param bulkUpdateFlowIds flows to be ignored when check endpoints.
     * @throws InvalidFlowException is thrown if a violation is found.
     */
    public void validate(RequestedFlow flow, Set<String> bulkUpdateFlowIds)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        baseFlowValidate(flow, bulkUpdateFlowIds);

        checkFlags(flow);
        checkBandwidth(flow);
        checkSwitchesSupportLldpAndArpIfNeeded(flow);

        if (StringUtils.isNotBlank(flow.getDiverseFlowId())) {
            checkDiverseFlow(flow);
        }

        if (StringUtils.isNotBlank(flow.getAffinityFlowId())) {
            checkAffinityFlow(flow);
        }

        validateFlowLoop(flow);

        //todo remove after noviflow fix
        validateQinQonWB(flow);
        checkFlowForLagPortConflict(flow);
    }

    private void validateFlowLoop(RequestedFlow requestedFlow) throws InvalidFlowException {
        if (requestedFlow.getLoopSwitchId() != null) {
            SwitchId loopSwitchId = requestedFlow.getLoopSwitchId();
            boolean loopSwitchIsTerminating = requestedFlow.getSrcSwitch().equals(loopSwitchId)
                    || requestedFlow.getDestSwitch().equals(loopSwitchId);
            if (!loopSwitchIsTerminating) {
                throw new InvalidFlowException("Loop switch is not terminating in flow path",
                        ErrorType.PARAMETERS_INVALID);
            }
        }
    }

    private void baseFlowValidate(RequestedFlow flow, Set<String> bulkUpdateFlowIds)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        final FlowEndpoint source = RequestedFlowMapper.INSTANCE.mapSource(flow);
        final FlowEndpoint destination = RequestedFlowMapper.INSTANCE.mapDest(flow);

        checkOneSwitchFlowConflict(source, destination);
        checkSwitchesExistsAndActive(flow.getSrcSwitch(), flow.getDestSwitch());

        for (EndpointDescriptor descriptor : new EndpointDescriptor[]{
                EndpointDescriptor.makeSource(source),
                EndpointDescriptor.makeDestination(destination)}) {
            SwitchId switchId = descriptor.endpoint.getSwitchId();
            SwitchProperties properties = switchPropertiesRepository.findBySwitchId(switchId)
                    .orElseThrow(() -> new InvalidFlowException(
                            format("Couldn't get switch properties for %s switch %s.", descriptor.name, switchId),
                            ErrorType.DATA_INVALID));

            // skip encapsulation type check for one switch flow
            if (!source.getSwitchId().equals(destination.getSwitchId())) {
                checkForEncapsulationTypeRequirement(descriptor, properties, flow.getFlowEncapsulationType());
            }
            checkForMultiTableRequirement(descriptor, properties);
            checkFlowForIslConflicts(descriptor);
            checkFlowForFlowConflicts(flow.getFlowId(), descriptor, bulkUpdateFlowIds);
            checkFlowForSinkEndpointConflicts(descriptor);
            checkFlowForMirrorEndpointConflicts(flow.getFlowId(), descriptor);
            checkFlowForServer42Conflicts(descriptor, properties);
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

        if (flow.isPeriodicPings() && flow.getSrcSwitch().equals(flow.getDestSwitch())) {
            throw new InvalidFlowException("Couldn't turn on periodic pings for one-switch flow",
                    ErrorType.PARAMETERS_INVALID);
        }

        if (flow.isIgnoreBandwidth() && flow.isStrictBandwidth()) {
            throw new InvalidFlowException("Can not turn on ignore bandwidth flag and strict bandwidth flag "
                    + "at the same time", ErrorType.PARAMETERS_INVALID);
        }
    }

    /**
     * Validates the specified flow when swap endpoint operation.
     */
    public void validateForSwapEndpoints(RequestedFlow firstFlow, RequestedFlow secondFlow)
            throws InvalidFlowException, UnavailableFlowEndpointException {
        baseFlowValidate(firstFlow, Sets.newHashSet(secondFlow.getFlowId()));
        baseFlowValidate(secondFlow, Sets.newHashSet(firstFlow.getFlowId()));

        checkForEqualsEndpoints(firstFlow, secondFlow);
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

    private void checkFlowForIslConflicts(EndpointDescriptor descriptor) throws InvalidFlowException {
        FlowEndpoint endpoint = descriptor.getEndpoint();
        if (!islRepository.findByEndpoint(endpoint.getSwitchId(), endpoint.getPortNumber()).isEmpty()) {
            String errorMessage = format(
                    "The port %d on the switch '%s' is occupied by an ISL (%s endpoint collision).",
                    endpoint.getPortNumber(), endpoint.getSwitchId(), descriptor.getName());
            throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }
    }

    private void checkFlowForSinkEndpointConflicts(EndpointDescriptor descriptor)
            throws InvalidFlowException {
        FlowEndpoint endpoint = descriptor.getEndpoint();
        Optional<FlowMirrorPath> foundFlowMirrorPath = flowMirrorPathRepository.findByEgressEndpoint(
                endpoint.getSwitchId(), endpoint.getPortNumber(), endpoint.getOuterVlanId(), endpoint.getInnerVlanId());
        if (foundFlowMirrorPath.isPresent()) {
            FlowMirrorPath flowMirrorPath = foundFlowMirrorPath.get();
            String errorMessage = format("Requested endpoint '%s' conflicts "
                            + "with existing flow mirror point '%s'.",
                    descriptor.getEndpoint(), flowMirrorPath.getPathId());
            throw new InvalidFlowException(errorMessage, ErrorType.ALREADY_EXISTS);
        }
    }

    private void checkFlowForMirrorEndpointConflicts(String flowId, EndpointDescriptor descriptor)
            throws InvalidFlowException {
        FlowEndpoint endpoint = descriptor.getEndpoint();
        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        if (foundFlow.isPresent()) {
            Flow flow = foundFlow.get();
            Optional<FlowMirrorPoints> flowMirrorPointsForward =
                    flowMirrorPointsRepository.findByPathIdAndSwitchId(flow.getForwardPathId(), endpoint.getSwitchId());
            Optional<FlowMirrorPoints> flowMirrorPointsReverse =
                    flowMirrorPointsRepository.findByPathIdAndSwitchId(flow.getReversePathId(), endpoint.getSwitchId());

            if ((flowMirrorPointsForward.isPresent() || flowMirrorPointsReverse.isPresent())
                    && (endpoint.isTrackLldpConnectedDevices() || endpoint.isTrackArpConnectedDevices())) {
                String errorMessage = format("Flow mirror point is created for the flow %s, "
                        + "lldp or arp can not be set to true.", flowId);
                throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
            }
        }
    }

    private void checkFlowForServer42Conflicts(EndpointDescriptor descriptor, SwitchProperties properties)
            throws InvalidFlowException {
        FlowEndpoint endpoint = descriptor.getEndpoint();
        if (endpoint.getPortNumber().equals(properties.getServer42Port())) {
            String errorMessage = format("Server 42 port in the switch properties for switch '%s' is set to '%d'. "
                            + "It is not possible to create or update an endpoint with these parameters.",
                    endpoint.getSwitchId(), properties.getServer42Port());
            throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }
    }

    private void checkFlowForFlowConflicts(String flowMirrorId, EndpointDescriptor descriptor)
            throws InvalidFlowException {
        checkFlowForFlowConflicts(flowMirrorId, descriptor, new HashSet<>());
    }

    /**
     * Checks a flow for endpoints' conflicts.
     *
     * @throws InvalidFlowException is thrown in a case when flow endpoints conflict with existing flows.
     */
    private void checkFlowForFlowConflicts(String flowId, EndpointDescriptor descriptor, Set<String> bulkUpdateFlowIds)
            throws InvalidFlowException {
        final FlowEndpoint endpoint = descriptor.getEndpoint();

        for (Flow entry : flowRepository.findByEndpoint(endpoint.getSwitchId(), endpoint.getPortNumber())) {
            if (flowId != null && (flowId.equals(entry.getFlowId()) || bulkUpdateFlowIds.contains(entry.getFlowId()))) {
                continue;
            }

            FlowEndpoint source = new FlowSourceAdapter(entry).getEndpoint();
            FlowEndpoint destination = new FlowDestAdapter(entry).getEndpoint();

            EndpointDescriptor conflict = null;
            if (endpoint.isSwitchPortVlanEquals(source)) {
                conflict = EndpointDescriptor.makeSource(source);
            } else if (endpoint.isSwitchPortVlanEquals(destination)) {
                conflict = EndpointDescriptor.makeDestination(destination);
            }

            if (conflict != null) {
                String errorMessage = format("Requested flow '%s' conflicts with existing flow '%s'. "
                                + "Details: requested flow '%s' %s: %s, "
                                + "existing flow '%s' %s: %s",
                        flowId, entry.getFlowId(),
                        flowId, descriptor.getName(), endpoint,
                        entry.getFlowId(), conflict.getName(), conflict.getEndpoint());
                throw new InvalidFlowException(errorMessage, ErrorType.ALREADY_EXISTS);
            }
        }
    }

    /**
     * Ensure switches are exists.
     *
     * @param sourceId source flow switch id to be validated.
     * @param destinationId destination flow switch id to be validated.
     * @throws UnavailableFlowEndpointException if switch not found.
     */
    @VisibleForTesting
    void checkSwitchesExistsAndActive(SwitchId sourceId, SwitchId destinationId)
            throws UnavailableFlowEndpointException {

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

    /**
     * Ensure vlans are not equal in the case when there is an attempt to create one-switch flow for a single port.
     */
    @VisibleForTesting
    private void checkOneSwitchFlowConflict(FlowEndpoint source, FlowEndpoint destination) throws InvalidFlowException {
        if (source.isSwitchPortVlanEquals(destination)) {
            throw new InvalidFlowException(
                    "It is not allowed to create one-switch flow for the same ports and vlans", ErrorType.DATA_INVALID);
        }
    }

    @VisibleForTesting
    void checkDiverseFlow(RequestedFlow targetFlow) throws InvalidFlowException {
        if (targetFlow.getSrcSwitch().equals(targetFlow.getDestSwitch())) {
            throw new InvalidFlowException("Couldn't add one-switch flow into diverse group",
                    ErrorType.PARAMETERS_INVALID);
        }

        Flow diverseFlow = flowRepository.findById(targetFlow.getDiverseFlowId())
                .orElseThrow(() ->
                        new InvalidFlowException(format("Failed to find diverse flow id %s",
                                targetFlow.getDiverseFlowId()), ErrorType.PARAMETERS_INVALID));


        if (StringUtils.isNotBlank(diverseFlow.getAffinityGroupId())) {
            String diverseFlowId = diverseFlow.getAffinityGroupId();
            diverseFlow = flowRepository.findById(diverseFlowId)
                    .orElseThrow(() ->
                            new InvalidFlowException(format("Failed to find diverse flow id %s", diverseFlowId),
                                    ErrorType.PARAMETERS_INVALID));


            Collection<String> affinityFlowIds = flowRepository
                    .findFlowsIdByAffinityGroupId(diverseFlow.getAffinityGroupId()).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (affinityFlowIds.contains(targetFlow.getAffinityFlowId())) {
                throw new InvalidFlowException("Couldn't create diverse group with flow in the same affinity group",
                        ErrorType.PARAMETERS_INVALID);
            }
        }

        if (diverseFlow.isOneSwitchFlow()) {
            throw new InvalidFlowException("Couldn't create diverse group with one-switch flow",
                    ErrorType.PARAMETERS_INVALID);
        }
    }

    @VisibleForTesting
    void checkAffinityFlow(RequestedFlow targetFlow) throws InvalidFlowException {
        if (targetFlow.getSrcSwitch().equals(targetFlow.getDestSwitch())) {
            throw new InvalidFlowException("Couldn't add one-switch flow into affinity group",
                    ErrorType.PARAMETERS_INVALID);
        }

        Flow affinityFlow = flowRepository.findById(targetFlow.getAffinityFlowId())
                .orElseThrow(() ->
                        new InvalidFlowException(format("Failed to find affinity flow id %s",
                                targetFlow.getAffinityFlowId()), ErrorType.PARAMETERS_INVALID));

        if (affinityFlow.isOneSwitchFlow()) {
            throw new InvalidFlowException("Couldn't create affinity group with one-switch flow",
                    ErrorType.PARAMETERS_INVALID);
        }

        if (StringUtils.isNotBlank(affinityFlow.getAffinityGroupId())) {
            String mainAffinityFlowId = affinityFlow.getAffinityGroupId();
            Flow mainAffinityFlow = flowRepository.findById(mainAffinityFlowId)
                    .orElseThrow(() ->
                            new InvalidFlowException(format("Failed to find main affinity flow id %s",
                                    mainAffinityFlowId), ErrorType.PARAMETERS_INVALID));

            if (StringUtils.isNotBlank(mainAffinityFlow.getDiverseGroupId())) {
                Collection<String> diverseFlowIds = flowRepository
                        .findFlowsIdByAffinityGroupId(mainAffinityFlow.getDiverseGroupId()).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                if (!diverseFlowIds.contains(targetFlow.getDiverseFlowId())) {
                    throw new InvalidFlowException("Couldn't create a diverse group with flow "
                            + "in a different diverse group than main affinity flow", ErrorType.PARAMETERS_INVALID);
                }
            }
        }
    }

    /**
     * Ensure switches support LLDP/ARP.
     *
     * @param requestedFlow a flow to be validated.
     */
    // TODO: switch to per endpoint based strategy (same as other enpoint related checks)
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

    /**
     * Check for equals endpoints.
     *
     * @param firstFlow a first flow.
     * @param secondFlow a second flow.
     */
    @VisibleForTesting
    void checkForEqualsEndpoints(RequestedFlow firstFlow, RequestedFlow secondFlow) throws InvalidFlowException {
        String message = "New requested endpoint for '%s' conflicts with existing endpoint for '%s'";

        Set<FlowEndpoint> firstFlowEndpoints = validateFlowEqualsEndpoints(firstFlow, message);
        Set<FlowEndpoint> secondFlowEndpoints = validateFlowEqualsEndpoints(secondFlow, message);

        for (FlowEndpoint endpoint : secondFlowEndpoints) {
            if (firstFlowEndpoints.contains(endpoint)) {
                message = String.format(message, secondFlow.getFlowId(), firstFlow.getFlowId());
                throw new InvalidFlowException(message, ErrorType.DATA_INVALID);
            }
        }
    }

    private Set<FlowEndpoint> validateFlowEqualsEndpoints(RequestedFlow flow, String errorMessage)
            throws InvalidFlowException {

        Set<FlowEndpoint> flowEndpoints = new HashSet<>();
        flowEndpoints.add(RequestedFlowMapper.INSTANCE.mapSource(flow));
        flowEndpoints.add(RequestedFlowMapper.INSTANCE.mapDest(flow));

        if (flowEndpoints.size() != 2) {
            errorMessage = String.format(errorMessage, flow.getFlowId(), flow.getFlowId());
            throw new InvalidFlowException(errorMessage, ErrorType.DATA_INVALID);
        }

        return flowEndpoints;
    }

    private void checkForMultiTableRequirement(EndpointDescriptor descriptor, SwitchProperties switchProperties)
            throws InvalidFlowException {
        FlowEndpoint endpoint = descriptor.getEndpoint();
        if (endpoint.getVlanStack().size() < 2) {
            return;
        }

        if (! switchProperties.isMultiTable()) {
            final String errorMessage = format(
                    "Flow's %s endpoint is double VLAN tagged, switch %s is not capable to support such endpoint "
                            + "encapsulation.",
                    descriptor.getName(), endpoint.getSwitchId());
            throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }
    }

    @VisibleForTesting
    void checkForEncapsulationTypeRequirement(
            EndpointDescriptor descriptor, SwitchProperties switchProperties, FlowEncapsulationType encapsulationType)
            throws InvalidFlowException {

        Set<FlowEncapsulationType> supportedEncapsulationTypes = Optional.ofNullable(
                switchProperties.getSupportedTransitEncapsulation()).orElse(new HashSet<>());
        if (encapsulationType != null && !supportedEncapsulationTypes.contains(encapsulationType)) {
            final String errorMessage = format(
                    "Flow's %s endpoint %s doesn't support requested encapsulation type %s. Choose one of the supported"
                            + " encapsulation types %s or update switch properties and add needed encapsulation type.",
                    descriptor.getName(), descriptor.endpoint.getSwitchId(), encapsulationType,
                    switchProperties.getSupportedTransitEncapsulation());
            throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @VisibleForTesting
    static class EndpointDescriptor {
        private final FlowEndpoint endpoint;
        private final String name;

        @VisibleForTesting
        static EndpointDescriptor makeSource(FlowEndpoint endpoint) {
            return new EndpointDescriptor(endpoint, "source");
        }

        private static EndpointDescriptor makeDestination(FlowEndpoint endpoint) {
            return new EndpointDescriptor(endpoint, "destination");
        }
    }

    /**
     * Validate the flow mirror point.
     */
    public void flowMirrorPointValidate(RequestedFlowMirrorPoint mirrorPoint)
            throws InvalidFlowException, UnavailableFlowEndpointException {

        checkSwitchesExistsAndActive(mirrorPoint.getMirrorPointSwitchId(), mirrorPoint.getSinkEndpoint().getSwitchId());

        EndpointDescriptor descriptor = EndpointDescriptor.makeDestination(FlowEndpoint.builder()
                .switchId(mirrorPoint.getSinkEndpoint().getSwitchId())
                .portNumber(mirrorPoint.getSinkEndpoint().getPortNumber())
                .outerVlanId(mirrorPoint.getSinkEndpoint().getOuterVlanId())
                .innerVlanId(mirrorPoint.getSinkEndpoint().getInnerVlanId())
                .build());

        SwitchId switchId = descriptor.endpoint.getSwitchId();
        SwitchProperties properties = switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new InvalidFlowException(
                        format("Couldn't get switch properties for %s switch %s.", descriptor.name, switchId),
                        ErrorType.DATA_INVALID));

        checkForConnectedDevisesConflict(mirrorPoint.getFlowId(), mirrorPoint.getMirrorPointSwitchId());
        checkForMultiTableRequirement(descriptor, properties);
        checkFlowForIslConflicts(descriptor);
        checkFlowForFlowConflicts(mirrorPoint.getMirrorPointId(), descriptor);
        checkFlowForSinkEndpointConflicts(descriptor);
        checkFlowForServer42Conflicts(descriptor, properties);
    }

    private void checkForConnectedDevisesConflict(String flowId, SwitchId switchId)
            throws InvalidFlowException {
        SwitchProperties properties = switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new InvalidFlowException(
                        format("Couldn't get switch properties for switch %s.", switchId),
                        ErrorType.DATA_INVALID));

        if (properties.isSwitchLldp() || properties.isSwitchArp()) {
            String errorMessage = format("Connected devices feature is active on the switch %s, "
                    + "flow mirror point cannot be created on this switch.", switchId);
            throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }

        Optional<Flow> foundFlow = flowRepository.findById(flowId);

        if (foundFlow.isPresent()) {
            Flow flow = foundFlow.get();
            FlowEndpoint source = new FlowSourceAdapter(flow).getEndpoint();
            FlowEndpoint destination = new FlowDestAdapter(flow).getEndpoint();

            if (switchId.equals(source.getSwitchId())) {
                if (source.isTrackLldpConnectedDevices() || source.isTrackArpConnectedDevices()) {
                    String errorMessage = format("Connected devices feature is active on the flow %s for endpoint %s, "
                                    + "flow mirror point cannot be created this flow",
                            flow.getFlowId(), source);
                    throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
                }
            } else {
                if (destination.isTrackLldpConnectedDevices() || destination.isTrackArpConnectedDevices()) {
                    String errorMessage = format("Connected devices feature is active on the flow %s for endpoint %s, "
                                    + "flow mirror point cannot be created this flow",
                            flow.getFlowId(), destination);
                    throw new InvalidFlowException(errorMessage, ErrorType.PARAMETERS_INVALID);
                }
            }
        }
    }

    @VisibleForTesting
    void validateQinQonWB(RequestedFlow requestedFlow)
            throws UnavailableFlowEndpointException, InvalidFlowException {
        final FlowEndpoint source = RequestedFlowMapper.INSTANCE.mapSource(requestedFlow);
        final FlowEndpoint destination = RequestedFlowMapper.INSTANCE.mapDest(requestedFlow);

        if (source.getInnerVlanId() != 0) {
            Switch srcSwitch = switchRepository.findById(source.getSwitchId())
                    .orElseThrow(() -> new UnavailableFlowEndpointException(format("Endpoint switch not found %s",
                            source.getSwitchId())));
            if (Switch.isNoviflowESwitch(srcSwitch.getOfDescriptionManufacturer(),
                    srcSwitch.getOfDescriptionHardware())) {
                String message = format("QinQ feature is temporary disabled for WB-series switch '%s'",
                        srcSwitch.getSwitchId());
                throw new InvalidFlowException(message, ErrorType.PARAMETERS_INVALID);
            }
        }

        if (destination.getInnerVlanId() != 0) {
            Switch destSwitch = switchRepository.findById(destination.getSwitchId())
                    .orElseThrow(() -> new UnavailableFlowEndpointException(format("Endpoint switch not found %s",
                            destination.getSwitchId())));
            if (Switch.isNoviflowESwitch(destSwitch.getOfDescriptionManufacturer(),
                    destSwitch.getOfDescriptionHardware())) {
                String message = format("QinQ feature is temporary disabled for WB-series switch '%s'",
                        destSwitch.getSwitchId());
                throw new InvalidFlowException(message, ErrorType.PARAMETERS_INVALID);
            }
        }
    }

    private void checkFlowForLagPortConflict(RequestedFlow requestedFlow) throws InvalidFlowException {
        FlowEndpoint source = RequestedFlowMapper.INSTANCE.mapSource(requestedFlow);
        FlowEndpoint destination = RequestedFlowMapper.INSTANCE.mapDest(requestedFlow);

        for (FlowEndpoint endpoint : new FlowEndpoint[]{source, destination}) {
            Optional<PhysicalPort> physicalPort = physicalPortRepository.findBySwitchIdAndPortNumber(
                    endpoint.getSwitchId(), endpoint.getPortNumber());
            if (physicalPort.isPresent()) {
                String message = format("Port %d on switch %s is used as part of LAG port %d",
                        endpoint.getPortNumber(), endpoint.getSwitchId(),
                        physicalPort.get().getLagLogicalPort().getLogicalPortNumber());
                throw new InvalidFlowException(message, ErrorType.PARAMETERS_INVALID);
            }
        }
    }
}
