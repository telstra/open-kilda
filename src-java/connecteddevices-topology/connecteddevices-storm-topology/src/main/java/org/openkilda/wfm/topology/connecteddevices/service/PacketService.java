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

package org.openkilda.wfm.topology.connecteddevices.service;

import static org.openkilda.model.ConnectedDeviceType.LLDP;
import static org.openkilda.model.Cookie.LLDP_INGRESS_COOKIE;
import static org.openkilda.model.Cookie.LLDP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_COOKIE;
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.Cookie.LLDP_TRANSIT_COOKIE;
import static org.openkilda.persistence.FetchStrategy.DIRECT_RELATIONS;

import org.openkilda.messaging.info.event.SwitchLldpInfoData;
import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import com.google.common.annotations.VisibleForTesting;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
public class PacketService {
    public static final int FULL_PORT_VLAN = 0;

    private TransactionManager transactionManager;
    private SwitchRepository switchRepository;
    private SwitchConnectedDeviceRepository switchConnectedDeviceRepository;
    private TransitVlanRepository transitVlanRepository;
    private FlowRepository flowRepository;

    public PacketService(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        switchConnectedDeviceRepository = persistenceManager.getRepositoryFactory()
                .createSwitchConnectedDeviceRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    /**
     * Handle Switch LLDP info data.
     */
    public void handleSwitchLldpData(SwitchLldpInfoData data) {
        transactionManager.doInTransaction(() -> {

            FlowRelatedData flowRelatedData = findFlowRelatedData(data);
            if (flowRelatedData == null) {
                return;
            }

            SwitchConnectedDevice device = getOrBuildSwitchDevice(data, flowRelatedData.originalVlan);

            if (device == null) {
                return;
            }

            device.setTtl(data.getTtl());
            device.setPortDescription(data.getPortDescription());
            device.setSystemName(data.getSystemName());
            device.setSystemDescription(data.getSystemDescription());
            device.setSystemCapabilities(data.getSystemCapabilities());
            device.setManagementAddress(data.getManagementAddress());
            device.setTimeLastSeen(Instant.ofEpochMilli(data.getTimestamp()));
            device.setFlowId(flowRelatedData.flowId);
            device.setSource(flowRelatedData.source);

            switchConnectedDeviceRepository.createOrUpdate(device);
        });
    }

    private FlowRelatedData findFlowRelatedData(SwitchLldpInfoData data) {
        if (data.getCookie() == LLDP_POST_INGRESS_COOKIE) {
            return findFlowRelatedDataForVlanFlow(data);
        } else if (data.getCookie() == LLDP_POST_INGRESS_VXLAN_COOKIE) {
            return findFlowRelatedDataForVxlanFlow(data);
        } else if (data.getCookie() == LLDP_POST_INGRESS_ONE_SWITCH_COOKIE) {
            return findFlowRelatedDataForOneSwitchFlow(data);
        } else if (data.getCookie() == LLDP_INPUT_PRE_DROP_COOKIE
                || data.getCookie() == LLDP_INGRESS_COOKIE
                || data.getCookie() == LLDP_TRANSIT_COOKIE) {
            int vlan = data.getVlans().isEmpty() ? 0 : data.getVlans().get(0);
            return new FlowRelatedData(vlan, null, null);
        }
        log.warn("Got LLDP packet from unknown rule with cookie {}. Switch {}, port {}, vlans {}",
                data.getCookie(), data.getSwitchId(), data.getPortNumber(), data.getVlans());
        return null;
    }

    @VisibleForTesting
    FlowRelatedData findFlowRelatedDataForVlanFlow(SwitchLldpInfoData data) {
        if (data.getVlans().isEmpty()) {
            log.warn("Got LLDP packet without transit VLAN: {}", data);
            return null;
        }
        int transitVlan = data.getVlans().get(0);
        Flow flow = findFlowByTransitVlan(transitVlan);

        if (flow == null) {
            return null;
        }

        int customerVlan = data.getVlans().size() > 1 ? data.getVlans().get(1) : 0;
        if (data.getSwitchId().equals(flow.getSrcSwitch().getSwitchId())) {
            if (flow.getSrcVlan() == FULL_PORT_VLAN) {
                // case 1:  customer vlan 0 ==> src vlan 0, transit vlan 2 ==> output vlan 2, vlans in packet: [2]
                // case 2:  customer vlan 1 ==> src vlan 0, transit vlan 2 ==> output vlan 2, vlans in packet: [2, 1]
                return new FlowRelatedData(customerVlan, flow.getFlowId(), true);
            } else {
                // case 1:  customer vlan 1 ==> src vlan 1, transit vlan 2 ==> output vlan 2, vlans in packet: [2]
                return new FlowRelatedData(flow.getSrcVlan(), flow.getFlowId(), true);
            }
        } else if (data.getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            if (flow.getDestVlan() == FULL_PORT_VLAN) {
                // case 1:  customer vlan 0 ==> dst vlan 0, transit vlan 2 ==> output vlan 2, vlans in packet: [2]
                // case 2:  customer vlan 1 ==> dst vlan 0, transit vlan 2 ==> output vlan 2, vlans in packet: [2, 1]
                return new FlowRelatedData(customerVlan, flow.getFlowId(), false);
            } else {
                // case 1:  customer vlan 1 ==> dst vlan 1, transit vlan 2 ==> output vlan 2, vlans in packet: [2]
                return new FlowRelatedData(flow.getDestVlan(), flow.getFlowId(), false);
            }
        } else {
            log.warn("Got LLDP packet from Flow {} on non-src/non-dst switch {}. Transit vlan: {}",
                    flow.getFlowId(), data.getSwitchId(), transitVlan);
            return null;
        }
    }

    @VisibleForTesting
    FlowRelatedData findFlowRelatedDataForVxlanFlow(SwitchLldpInfoData data) {
        int inputVlan = data.getVlans().isEmpty() ? 0 : data.getVlans().get(0);
        Flow flow = getFlowBySwitchIdPortAndVlan(data.getSwitchId(), data.getPortNumber(), inputVlan);

        if (flow == null) {
            return null;
        }

        if (data.getSwitchId().equals(flow.getSrcSwitch().getSwitchId())) {
            return new FlowRelatedData(inputVlan, flow.getFlowId(), true);
        } else if (data.getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            return new FlowRelatedData(inputVlan, flow.getFlowId(), false);
        } else {
            log.warn("Got LLDP packet from Flow {} on non-src/non-dst switch {}. Port number {}, input vlan {}",
                    flow.getFlowId(), data.getSwitchId(), data.getPortNumber(), inputVlan);
            return null;
        }
    }

    @VisibleForTesting
    FlowRelatedData findFlowRelatedDataForOneSwitchFlow(SwitchLldpInfoData data) {
        // top vlan with which we got LLDP packet in Floodlight.
        int outputVlan = data.getVlans().isEmpty() ? 0 : data.getVlans().get(0);
        // second vlan with which we got LLDP packet in Floodlight. Exists only for some full port flows.
        int customerVlan = data.getVlans().size() > 1 ? data.getVlans().get(1) : 0;
        Flow flow = getFlowBySwitchIdInPortAndOutVlan(data.getSwitchId(), data.getPortNumber(), outputVlan);

        if (flow == null) {
            return null;
        }

        if (!flow.isOneSwitchFlow()) {
            log.warn("Found NOT one switch flow {} by SwitchId {}, port number {}, vlan {} from LLDP packet",
                    flow.getFlowId(), data.getSwitchId(), data.getPortNumber(), outputVlan);
            return null;
        }

        if (flow.getSrcPort() == flow.getDestPort()) {
            return getOneSwitchOnePortFlowRelatedData(flow, outputVlan, customerVlan, data);
        }

        if (data.getPortNumber() == flow.getSrcPort()) {
            if (flow.getSrcVlan() == FULL_PORT_VLAN) {
                if (flow.getDestVlan() == FULL_PORT_VLAN) {
                    // case 1:  customer vlan 0 ==> src vlan 0, dst vlan 0 ==> output vlan 0, vlans in packet: []
                    // case 2:  customer vlan 1 ==> src vlan 0, dst vlan 0 ==> output vlan 1, vlans in packet: [1]
                    return new FlowRelatedData(outputVlan, flow.getFlowId(), true);
                } else {
                    // case 1:  customer vlan 0 ==> src vlan 0, dst vlan 2 ==> output vlan 2, vlans in packet: [2]
                    // case 2:  customer vlan 1 ==> src vlan 0, dst vlan 2 ==> output vlan 2, vlans in packet: [2, 1]
                    return new FlowRelatedData(customerVlan, flow.getFlowId(), true);
                }
            } else {
                // case 1:  customer vlan 1 ==> src vlan 1, dst vlan 2 ==> output vlan 2, vlans in packet: [2]
                return new FlowRelatedData(flow.getSrcVlan(), flow.getFlowId(), true);
            }
        } else if (data.getPortNumber() == flow.getDestPort()) {
            if (flow.getDestVlan() == FULL_PORT_VLAN) {
                if (flow.getSrcVlan() == FULL_PORT_VLAN) {
                    // case 1:  customer vlan 0 ==> dst vlan 0, src vlan 0 ==> output vlan 0, vlans in packet: []
                    // case 1:  customer vlan 1 ==> dst vlan 0, src vlan 0 ==> output vlan 1, vlans in packet: [1]
                    return new FlowRelatedData(outputVlan, flow.getFlowId(), false);
                } else {
                    // case 1:  customer vlan 0 ==> dst vlan 0, src vlan 2 ==> output vlan 2, vlans in packet: [2]
                    // case 2:  customer vlan 1 ==> dst vlan 0, src vlan 2 ==> output vlan 2, vlans in packet: [2, 1]
                    return new FlowRelatedData(customerVlan, flow.getFlowId(), false);
                }
            } else {
                // case 1:  customer vlan 1 ==> dst vlan 1, src vlan 2 ==> output vlan 2, vlans in packet: [2]
                return new FlowRelatedData(flow.getDestVlan(), flow.getFlowId(), false);
            }
        }

        log.warn("Got LLDP packet from one switch flow {} with non-src/non-dst vlan {}. SwitchId {}, "
                + "port number {}", flow.getFlowId(), outputVlan, data.getSwitchId(), data.getPortNumber());
        return null;
    }

    private FlowRelatedData getOneSwitchOnePortFlowRelatedData(
            Flow flow, int outputVlan, int customerVlan, SwitchLldpInfoData data) {
        if (flow.getDestVlan() == outputVlan) {
            if (flow.getSrcVlan() == FULL_PORT_VLAN) {
                // case 1:  customer vlan 0 ==> src vlan 0, dst vlan 2 ==> output vlan 2, vlans in packet: [2]
                // case 2:  customer vlan 1 ==> src vlan 0, dst vlan 2 ==> output vlan 2, vlans in packet: [2, 1]
                return new FlowRelatedData(customerVlan, flow.getFlowId(), true);
            } else {
                // case 1:  customer vlan 1 ==> src vlan 1, dst vlan 2 ==> output vlan 2, vlans in packet: [2]
                return new FlowRelatedData(flow.getSrcVlan(), flow.getFlowId(), true);
            }
        } else if (flow.getSrcVlan() == outputVlan) {
            if (flow.getDestVlan() == FULL_PORT_VLAN) {
                // case 1:  customer vlan 0 ==> dst vlan 0, src vlan 2 ==> output vlan 2, vlans in packet: [2]
                // case 2:  customer vlan 1 ==> dst vlan 0, src vlan 2 ==> output vlan 2, vlans in packet: [2, 1]
                return new FlowRelatedData(customerVlan, flow.getFlowId(), false);
            } else {
                // case 1:  customer vlan 1 ==> dst vlan 1, src vlan 2 ==> output vlan 2, vlans in packet: [2]
                return new FlowRelatedData(flow.getDestVlan(), flow.getFlowId(), false);
            }
        }
        log.warn("Got LLDP data for one switch one Flow with unknown output vlan {}. Flow {} Data {}",
                outputVlan, flow.getFlowId(), data);
        return null;
    }

    private Flow findFlowByTransitVlan(int vlan) {
        Optional<TransitVlan> transitVlan = transitVlanRepository.findByVlan(vlan);

        if (!transitVlan.isPresent()) {
            log.info("Couldn't find flow encapsulation resources by Transit vlan '{}", vlan);
            return null;
        }
        Optional<Flow> flow = flowRepository.findById(transitVlan.get().getFlowId(), DIRECT_RELATIONS);
        if (!flow.isPresent()) {
            log.warn("Couldn't find flow by flow ID '{}", transitVlan.get().getFlowId());
            return null;
        }
        return flow.get();
    }

    private Flow getFlowBySwitchIdPortAndVlan(SwitchId switchId, int portNumber, int vlan) {
        Optional<Flow> flow = flowRepository.findByEndpointAndVlan(switchId, portNumber, vlan);

        if (flow.isPresent()) {
            return flow.get();
        } else {
            // may be it's a full port flow
            Optional<Flow> fullPortFlow = flowRepository.findByEndpointAndVlan(switchId, portNumber, FULL_PORT_VLAN);
            if (fullPortFlow.isPresent()) {
                return fullPortFlow.get();
            } else {
                log.warn("Couldn't find Flow for LLDP packet on endpoint: Switch {}, port {}, vlan {}",
                        switchId, portNumber, vlan);
                return null;
            }
        }
    }

    private Flow getFlowBySwitchIdInPortAndOutVlan(SwitchId switchId, int inPort, int outVlan) {
        Optional<Flow> flow = flowRepository.findBySwitchIdInPortAndOutVlan(switchId, inPort, outVlan);

        if (flow.isPresent()) {
            return flow.get();
        } else {
            // may be it's a full port flow
            Optional<Flow> fullPortFlow = flowRepository.findBySwitchIdInPortAndOutVlan(
                    switchId, inPort, FULL_PORT_VLAN);
            if (fullPortFlow.isPresent()) {
                return fullPortFlow.get();
            } else {
                log.warn("Couldn't find Flow for LLDP packet by: Switch {}, InPort {}, OutVlan {}",
                        switchId, inPort, outVlan);
                return null;
            }
        }
    }

    private SwitchConnectedDevice getOrBuildSwitchDevice(SwitchLldpInfoData data, int vlan) {
        Optional<SwitchConnectedDevice> device = switchConnectedDeviceRepository
                .findLldpByUniqueFieldCombination(
                        data.getSwitchId(), data.getPortNumber(), vlan, data.getMacAddress(),
                        data.getChassisId(), data.getPortId());

        if (device.isPresent()) {
            return device.get();
        }

        Optional<Switch> sw = switchRepository.findById(data.getSwitchId());

        if (!sw.isPresent()) {
            log.warn("Got LLDP packet from non existent switch {}. Port number '{}', vlan '{}', mac address '{}', "
                            + "chassis id '{}', port id '{}'", data.getSwitchId(), data.getPortNumber(), vlan,
                    data.getMacAddress(), data.getChassisId(), data.getPortId());
            return null;
        }

        return SwitchConnectedDevice.builder()
                .switchObj(sw.get())
                .portNumber(data.getPortNumber())
                .vlan(vlan)
                .macAddress(data.getMacAddress())
                .type(LLDP)
                .chassisId(data.getChassisId())
                .portId(data.getPortId())
                .timeFirstSeen(Instant.ofEpochMilli(data.getTimestamp()))
                .build();
    }

    @Value
    static class FlowRelatedData {
        int originalVlan;
        String flowId;
        Boolean source; // device connected to source of Flow or to destination
    }
}
