/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.services;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import org.openkilda.messaging.model.SwitchAvailabilityData;
import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.messaging.model.SwitchPropertiesDto;
import org.openkilda.messaging.nbtopology.response.GetSwitchResponse;
import org.openkilda.messaging.nbtopology.response.SwitchConnectionsResponse;
import org.openkilda.messaging.nbtopology.response.SwitchPropertiesResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchConnect;
import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchProperties.RttState;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.persistence.repositories.PortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchConnectRepository;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.error.SwitchPropertiesNotFoundException;
import org.openkilda.wfm.share.mappers.SwitchMapper;
import org.openkilda.wfm.share.mappers.SwitchPropertiesMapper;
import org.openkilda.wfm.topology.nbworker.exceptions.IllegalSwitchPropertiesException;
import org.openkilda.wfm.topology.nbworker.exceptions.IllegalSwitchStateException;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SwitchOperationsService {

    private SwitchOperationsServiceCarrier carrier;
    private SwitchRepository switchRepository;
    private SwitchPropertiesRepository switchPropertiesRepository;
    private SwitchConnectRepository switchConnectRepository;
    private PortPropertiesRepository portPropertiesRepository;
    private SwitchConnectedDeviceRepository switchConnectedDeviceRepository;
    private LagLogicalPortRepository lagLogicalPortRepository;
    private FlowMirrorPointsRepository flowMirrorPointsRepository;
    private FlowMirrorPathRepository flowMirrorPathRepository;
    private TransactionManager transactionManager;
    private LinkOperationsService linkOperationsService;
    private IslRepository islRepository;
    private FlowRepository flowRepository;
    private FlowPathRepository flowPathRepository;
    private PhysicalPortRepository physicalPortRepository;
    private PortRepository portRepository;

    public SwitchOperationsService(
            RepositoryFactory repositoryFactory, TransactionManager transactionManager,
            SwitchOperationsServiceCarrier carrier, ILinkOperationsServiceCarrier linksCarrier) {
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.transactionManager = transactionManager;
        this.linkOperationsService
                = new LinkOperationsService(linksCarrier, repositoryFactory, transactionManager);
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
        this.portPropertiesRepository = repositoryFactory.createPortPropertiesRepository();
        this.switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        this.switchConnectRepository = repositoryFactory.createSwitchConnectRepository();
        this.switchConnectedDeviceRepository = repositoryFactory.createSwitchConnectedDeviceRepository();
        this.lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        this.flowMirrorPointsRepository = repositoryFactory.createFlowMirrorPointsRepository();
        this.flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();
        this.physicalPortRepository = repositoryFactory.createPhysicalPortRepository();
        this.portRepository = repositoryFactory.createPortRepository();
        this.carrier = carrier;
    }

    /**
     * Get switch by switch id.
     *
     * @param switchId switch id.
     */
    public GetSwitchResponse getSwitch(SwitchId switchId) throws SwitchNotFoundException {
        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        switchRepository.detach(sw);
        return new GetSwitchResponse(sw);
    }

    /**
     * Return all switches.
     *
     * @return all switches.
     */
    public List<GetSwitchResponse> getAllSwitches() {
        return switchRepository.findAll().stream()
                .peek(aSwitch -> switchRepository.detach(aSwitch))
                .map(GetSwitchResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * Update the "Under maintenance" flag for the switch.
     *
     * @param switchId         switch id.
     * @param underMaintenance "Under maintenance" flag.
     * @return updated switch.
     * @throws SwitchNotFoundException if there is no switch with this switch id.
     */
    public Switch updateSwitchUnderMaintenanceFlag(SwitchId switchId, boolean underMaintenance)
            throws SwitchNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Optional<Switch> foundSwitch = switchRepository.findById(switchId);
            if (!(foundSwitch.isPresent())) {
                return Optional.<Switch>empty();
            }

            Switch sw = foundSwitch.get();

            if (sw.isUnderMaintenance() == underMaintenance) {
                switchRepository.detach(sw);
                return Optional.of(sw);
            }

            sw.setUnderMaintenance(underMaintenance);

            linkOperationsService.getAllIsls(switchId, null, null, null)
                    .forEach(isl -> {
                        try {
                            linkOperationsService.updateLinkUnderMaintenanceFlag(
                                    isl.getSrcSwitchId(),
                                    isl.getSrcPort(),
                                    isl.getDestSwitchId(),
                                    isl.getDestPort(),
                                    underMaintenance);
                        } catch (IslNotFoundException e) {
                            //We get all ISLs on this switch, so all ISLs exist
                        }
                    });

            switchRepository.detach(sw);
            return Optional.of(sw);
        }).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

    /**
     * Delete switch.
     *
     * @param switchId ID of switch to be deleted
     * @param force    if True all switch relationships will be deleted too.
     *                 If False switch will be deleted only if it has no relations.
     * @return True if switch was deleted, False otherwise
     * @throws SwitchNotFoundException if switch is not found
     */
    public boolean deleteSwitch(SwitchId switchId, boolean force) throws SwitchNotFoundException {
        transactionManager.doInTransaction(() -> {
            Switch sw = switchRepository.findById(switchId)
                    .orElseThrow(() -> new SwitchNotFoundException(switchId));

            switchPropertiesRepository.findBySwitchId(sw.getSwitchId())
                    .ifPresent(sp -> switchPropertiesRepository.remove(sp));
            portPropertiesRepository.getAllBySwitchId(sw.getSwitchId())
                    .forEach(portPropertiesRepository::remove);
            portRepository.getAllBySwitchId(sw.getSwitchId())
                    .forEach(portRepository::remove);
            if (force) {
                // remove() removes switch along with all relationships.
                switchRepository.remove(sw);
            } else {
                // removeIfNoDependant() is used to be sure that we wouldn't delete switch
                // if it has even one relationship.
                switchRepository.removeIfNoDependant(sw);
            }
        });

        return !switchRepository.exists(switchId);
    }

    /**
     * Check that switch is not in 'Active' state.
     *
     * @throws SwitchNotFoundException     if there is no such switch.
     * @throws IllegalSwitchStateException if switch is in 'Active' state
     */
    public void checkSwitchIsDeactivated(SwitchId switchId)
            throws SwitchNotFoundException, IllegalSwitchStateException {
        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));

        if (sw.getStatus() == SwitchStatus.ACTIVE) {
            String message = format("Switch '%s' is in 'Active' state.", switchId);
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    /**
     * Check that switch has no Flow relations.
     *
     * @throws IllegalSwitchStateException if switch has Flow relations
     */
    public void checkSwitchHasNoFlows(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<Flow> flows = flowRepository.findByEndpointSwitch(switchId);

        if (!flows.isEmpty()) {
            Set<String> flowIds = flows.stream()
                    .map(Flow::getFlowId)
                    .collect(Collectors.toSet());

            String message = format("Switch '%s' has %d assigned flows: %s.",
                    switchId, flowIds.size(), flowIds);
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    /**
     * Check that switch has no Flow Segment relations.
     *
     * @throws IllegalSwitchStateException if switch has Flow Segment relations
     */
    public void checkSwitchHasNoFlowSegments(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<FlowPath> flowPaths = flowPathRepository.findBySegmentSwitch(switchId);

        if (!flowPaths.isEmpty()) {
            String message = format("Switch '%s' has %d assigned rules. It must be freed first.",
                    switchId, flowPaths.size());
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    /**
     * Check that switch has no ISL relations.
     *
     * @throws IllegalSwitchStateException if switch has ISL relations
     */
    public void checkSwitchHasNoIsls(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<Isl> outgoingIsls = islRepository.findBySrcSwitch(switchId);
        Collection<Isl> ingoingIsls = islRepository.findByDestSwitch(switchId);

        if (!outgoingIsls.isEmpty() || !ingoingIsls.isEmpty()) {
            long activeIslCount = Stream.concat(outgoingIsls.stream(), ingoingIsls.stream())
                    .filter(isl -> isl.getStatus() == IslStatus.ACTIVE)
                    .count();

            if (activeIslCount > 0) {
                String message = format("Switch '%s' has %d active links. Unplug and remove them first.",
                        switchId, activeIslCount);
                throw new IllegalSwitchStateException(switchId.toString(), message);
            } else {
                String message = format("Switch '%s' has %d inactive links. Remove them first.",
                        switchId, outgoingIsls.size() + ingoingIsls.size());
                throw new IllegalSwitchStateException(switchId.toString(), message);
            }
        }
    }

    /**
     * Get switch properties.
     *
     * @param switchId target switch id
     * @throws SwitchPropertiesNotFoundException if switch properties is not found by switch id
     */
    public SwitchPropertiesDto getSwitchProperties(SwitchId switchId) {
        Optional<SwitchProperties> result = switchPropertiesRepository.findBySwitchId(switchId);
        return result.map(SwitchPropertiesMapper.INSTANCE::map)
                .orElseThrow(() -> new SwitchPropertiesNotFoundException(switchId));
    }

    /**
     * Get all switch properties.
     *
     * @throws SwitchPropertiesNotFoundException if switch properties is not found by switch id
     */
    public List<SwitchPropertiesResponse> getSwitchProperties() {
        Collection<SwitchProperties> result = switchPropertiesRepository.findAll();

        return result.stream().map(SwitchPropertiesMapper.INSTANCE::map)
                .map(x -> new SwitchPropertiesResponse(x)).collect(Collectors.toList());
    }

    /**
     * Update switch properties.
     *
     * @param switchId            target switch id
     * @param switchPropertiesDto switch properties
     * @throws IllegalSwitchPropertiesException  if switch properties are incorrect
     * @throws SwitchPropertiesNotFoundException if switch properties is not found by switch id
     */
    public SwitchPropertiesDto updateSwitchProperties(SwitchId switchId, SwitchPropertiesDto switchPropertiesDto) {
        if (isEmpty(switchPropertiesDto.getSupportedTransitEncapsulation())) {
            throw new IllegalSwitchPropertiesException("Supported transit encapsulations should not be null or empty");
        }
        SwitchProperties update = SwitchPropertiesMapper.INSTANCE.map(switchPropertiesDto);
        UpdateSwitchPropertiesResult result = transactionManager.doInTransaction(() -> {
            SwitchProperties switchProperties = switchPropertiesRepository.findBySwitchId(switchId)
                    .orElseThrow(() -> new SwitchPropertiesNotFoundException(switchId));
            final SwitchProperties oldProperties = new SwitchProperties(switchProperties);

            validateSwitchProperties(switchId, update);

            // must be called before updating of switchProperties object
            final boolean isSwitchSyncNeeded = isSwitchSyncNeeded(switchProperties, update);

            switchProperties.setMultiTable(update.isMultiTable());
            switchProperties.setSwitchLldp(update.isSwitchLldp());
            switchProperties.setSwitchArp(update.isSwitchArp());
            switchProperties.setSupportedTransitEncapsulation(update.getSupportedTransitEncapsulation());
            switchProperties.setServer42FlowRtt(update.isServer42FlowRtt());
            switchProperties.setServer42IslRtt(update.getServer42IslRtt());
            switchProperties.setServer42Port(update.getServer42Port());
            switchProperties.setServer42Vlan(update.getServer42Vlan());
            switchProperties.setServer42MacAddress(update.getServer42MacAddress());

            log.info("Updating {} switch properties from {} to {}. Is switch sync needed: {}",
                    switchId, oldProperties, switchProperties, isSwitchSyncNeeded);
            return new UpdateSwitchPropertiesResult(
                    SwitchPropertiesMapper.INSTANCE.map(switchProperties), isSwitchSyncNeeded);
        });

        if (result.isSwitchSyncRequired()) {
            carrier.requestSwitchSync(switchId);
        }

        if (switchPropertiesDto.isServer42FlowRtt()) {
            carrier.enableServer42FlowRttOnSwitch(switchId);
        } else {
            carrier.disableServer42FlowRttOnSwitch(switchId);
        }

        if (switchPropertiesDto.getServer42IslRtt() != SwitchPropertiesDto.RttState.DISABLED) {
            carrier.enableServer42IslRttOnSwitch(switchId);
        } else {
            carrier.disableServer42IslRttOnSwitch(switchId);
        }

        return result.switchPropertiesDto;
    }

    private boolean isSwitchSyncNeeded(SwitchProperties current, SwitchProperties updated) {
        boolean server42RttChanged = current.isServer42FlowRtt() != updated.isServer42FlowRtt()
                || current.getServer42IslRtt() != updated.getServer42IslRtt();
        boolean server42PropsChanged = (updated.isServer42FlowRtt() || updated.getServer42IslRtt() != RttState.DISABLED)
                && (!Objects.equals(current.getServer42Port(), updated.getServer42Port())
                || !Objects.equals(current.getServer42MacAddress(), updated.getServer42MacAddress())
                || !Objects.equals(current.getServer42Vlan(), updated.getServer42Vlan()));

        return current.isMultiTable() != updated.isMultiTable()
                || current.isSwitchLldp() != updated.isSwitchLldp()
                || current.isSwitchArp() != updated.isSwitchArp()
                || server42RttChanged
                || server42PropsChanged;
    }

    private void validateSwitchProperties(SwitchId switchId, SwitchProperties updatedSwitchProperties) {
        if (!updatedSwitchProperties.isMultiTable()) {
            String propertyErrorMessage = "Illegal switch properties combination for switch %s. '%s' property "
                    + "can be set to 'true' only if 'multiTable' property is 'true'.";
            if (updatedSwitchProperties.isSwitchLldp()) {
                throw new IllegalSwitchPropertiesException(format(propertyErrorMessage, switchId, "switchLldp"));
            }

            if (updatedSwitchProperties.isSwitchArp()) {
                throw new IllegalSwitchPropertiesException(format(propertyErrorMessage, switchId, "switchArp"));
            }

            List<String> flowsWitchEnabledLldp = flowRepository.findByEndpointSwitchWithEnabledLldp(switchId).stream()
                    .map(Flow::getFlowId)
                    .collect(Collectors.toList());

            if (!flowsWitchEnabledLldp.isEmpty()) {
                throw new IllegalSwitchPropertiesException(
                        format("Illegal switch properties combination for switch %s. "
                                + "Detect Connected Devices feature is turn on for following flows [%s]. "
                                + "For correct work of this feature switch property 'multiTable' must be set to 'true' "
                                + "Please disable detecting of connected devices via LLDP for each flow before set "
                                + "'multiTable' property to 'false'",
                                switchId, String.join(", ", flowsWitchEnabledLldp)));
            }

            List<String> flowsWithEnabledArp = flowRepository.findByEndpointSwitchWithEnabledArp(switchId).stream()
                    .map(Flow::getFlowId)
                    .collect(Collectors.toList());

            if (!flowsWithEnabledArp.isEmpty()) {
                throw new IllegalSwitchPropertiesException(
                        format("Illegal switch properties combination for switch %s. "
                                + "Detect Connected Devices feature via ARP is turn on for following flows [%s]. "
                                + "For correct work of this feature switch property 'multiTable' must be set to 'true' "
                                + "Please disable detecting of connected devices via ARP for each flow before set "
                                + "'multiTable' property to 'false'",
                                switchId, String.join(", ", flowsWithEnabledArp)));
            }
        }

        if (updatedSwitchProperties.getServer42Port() != null) {
            Optional<PhysicalPort> physicalPort = physicalPortRepository.findBySwitchIdAndPortNumber(
                    switchId, updatedSwitchProperties.getServer42Port());
            if (physicalPort.isPresent()) {
                throw new IllegalSwitchPropertiesException(
                        format("Illegal server42 port '%d' on switch %s. This port is part of LAG '%d'. Please "
                                        + "delete LAG port or choose another server42 port.",
                                updatedSwitchProperties.getServer42Port(), switchId,
                                physicalPort.get().getLagLogicalPort().getLogicalPortNumber()));
            }
        }

        if (updatedSwitchProperties.isServer42FlowRtt()) {
            String errorMessage = "Illegal switch properties combination for switch %s. To enable property "
                    + "'server42_flow_rtt' you need to specify valid property '%s'";
            if (updatedSwitchProperties.getServer42Port() == null) {
                throw new IllegalSwitchPropertiesException(format(errorMessage, switchId, "server42_port"));
            }
            if (updatedSwitchProperties.getServer42MacAddress() == null) {
                throw new IllegalSwitchPropertiesException(format(errorMessage, switchId, "server42_mac_address"));
            }
            if (updatedSwitchProperties.getServer42Vlan() == null) {
                throw new IllegalSwitchPropertiesException(format(errorMessage, switchId, "server42_vlan"));
            }
        }

        if (updatedSwitchProperties.getServer42IslRtt() == RttState.ENABLED) {
            String errorMessage = "Illegal switch properties combination for switch %s. To enable property "
                    + "'server42_isl_rtt' you need to specify valid property '%s'";
            if (updatedSwitchProperties.getServer42Port() == null) {
                throw new IllegalSwitchPropertiesException(format(errorMessage, switchId, "server42_port"));
            }
            if (updatedSwitchProperties.getServer42MacAddress() == null) {
                throw new IllegalSwitchPropertiesException(format(errorMessage, switchId, "server42_mac_address"));
            }
            if (updatedSwitchProperties.getServer42Vlan() == null) {
                throw new IllegalSwitchPropertiesException(format(errorMessage, switchId, "server42_vlan"));
            }
        }

        Collection<FlowMirrorPoints> flowMirrorPoints = flowMirrorPointsRepository.findBySwitchId(switchId);
        if (!flowMirrorPoints.isEmpty()
                && (updatedSwitchProperties.isSwitchLldp() || updatedSwitchProperties.isSwitchArp())) {
            throw new IllegalSwitchPropertiesException(format("Flow mirror point is created on the switch %s, "
                    + "switchLldp or switchArp can not be set to true.", switchId));
        }

        if (updatedSwitchProperties.getServer42Port() != null) {
            String errorMessage = "SwitchId '%s' and port '%d' belong to the %s endpoint. "
                    + "Cannot specify port '%d' as port for server 42.";
            int server42port = updatedSwitchProperties.getServer42Port();
            Collection<Flow> flows = flowRepository.findByEndpoint(switchId, server42port);
            if (!flows.isEmpty()) {
                throw new IllegalSwitchPropertiesException(
                        format(errorMessage, switchId, server42port, "flow", server42port));
            }

            Collection<FlowMirrorPath> flowMirrorPaths = flowMirrorPathRepository.findByEgressSwitchIdAndPort(switchId,
                    server42port);
            if (!flowMirrorPaths.isEmpty()) {
                throw new IllegalSwitchPropertiesException(
                        format(errorMessage, switchId, server42port, "flow mirror path", server42port));
            }
        }
    }

    /**
     * Get port properties.
     *
     * @param switchId target switch id
     * @param port     port number
     */
    public PortProperties getPortProperties(SwitchId switchId, int port) throws SwitchNotFoundException {
        return portPropertiesRepository.getBySwitchIdAndPort(switchId, port)
                .orElse(PortProperties.builder()
                        .switchObj(switchRepository.findById(switchId)
                                .orElseThrow(() -> new SwitchNotFoundException(switchId)))
                        .port(port)
                        .build());
    }

    /**
     * Get switch connected devices.
     *
     * @param switchId target switch id
     */
    public Collection<SwitchConnectedDevice> getSwitchConnectedDevices(
            SwitchId switchId) throws SwitchNotFoundException {
        return transactionManager.doInTransaction(() -> {
            if (!switchRepository.exists(switchId)) {
                throw new SwitchNotFoundException(switchId);
            }

            return switchConnectedDeviceRepository.findBySwitchId(switchId);
        });
    }

    /**
     * Get switch LAG ports.
     *
     * @param switchId target switch id
     */
    public Collection<LagLogicalPort> getSwitchLagPorts(SwitchId switchId) throws SwitchNotFoundException {
        return transactionManager.doInTransaction(() -> {
            if (!switchRepository.exists(switchId)) {
                throw new SwitchNotFoundException(switchId);
            }
            return lagLogicalPortRepository.findBySwitchId(switchId);
        });
    }

    /**
     * Find and return all {@code IslEndpoint} for all ISL detected for this switch.
     */
    public List<IslEndpoint> getSwitchIslEndpoints(SwitchId switchId) {
        return islRepository.findBySrcSwitch(switchId).stream()
                .map(isl -> new IslEndpoint(switchId, isl.getSrcPort()))
                .collect(Collectors.toList());
    }

    /**
     * Patch switch.
     */
    public Switch patchSwitch(SwitchId switchId, SwitchPatch data) throws SwitchNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Switch foundSwitch = switchRepository.findById(switchId)
                    .orElseThrow(() -> new SwitchNotFoundException(switchId));

            Optional.ofNullable(data.getPop()).ifPresent(pop -> foundSwitch.setPop(!"".equals(pop) ? pop : null));
            Optional.ofNullable(data.getLocation()).ifPresent(location -> {
                Optional.ofNullable(location.getLatitude()).ifPresent(foundSwitch::setLatitude);
                Optional.ofNullable(location.getLongitude()).ifPresent(foundSwitch::setLongitude);
                Optional.ofNullable(location.getStreet()).ifPresent(foundSwitch::setStreet);
                Optional.ofNullable(location.getCity()).ifPresent(foundSwitch::setCity);
                Optional.ofNullable(location.getCountry()).ifPresent(foundSwitch::setCountry);
            });
            switchRepository.detach(foundSwitch);
            return foundSwitch;
        });
    }

    /**
     * Find and return the set of connections to the speakers for specific switch.
     */
    public SwitchConnectionsResponse getSwitchConnections(SwitchId switchId) throws SwitchNotFoundException {
        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        SwitchAvailabilityData.SwitchAvailabilityDataBuilder payload = SwitchAvailabilityData.builder();
        for (SwitchConnect entry : switchConnectRepository.findBySwitchId(switchId)) {
            payload.connection(SwitchMapper.INSTANCE.map(entry));
        }
        return new SwitchConnectionsResponse(sw.getSwitchId(), sw.getStatus(), payload.build());
    }

    @Value
    private class UpdateSwitchPropertiesResult {
        private SwitchPropertiesDto switchPropertiesDto;
        private boolean switchSyncRequired;

    }
}
