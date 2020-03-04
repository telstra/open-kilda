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

package org.openkilda.persistence.repositories;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.FetchStrategy;

import java.util.Collection;
import java.util.Optional;

public interface FlowRepository extends Repository<Flow> {
    long countFlows();

    /**
     * Fetches all flows.
     * <p/>
     * IMPORTANT: the method doesn't complete the flow and flow path entities with related path segments!
     */
    Collection<Flow> findAll();

    boolean exists(String flowId);

    Optional<Flow> findById(String flowId);

    Optional<Flow> findById(String flowId, FetchStrategy fetchStrategy);

    Collection<Flow> findByGroupId(String flowGroupId);

    Collection<String> findFlowsIdByGroupId(String flowGroupId);

    Collection<Flow> findWithPeriodicPingsEnabled();

    Collection<Flow> findByEndpoint(SwitchId switchId, int port);

    /**
     * Find flow by endpoint (SwitchId, port and vlan).
     * <p/>
     * IMPORTANT: the method doesn't complete the flow and flow path entities with related path segments!
     */
    Optional<Flow> findByEndpointAndVlan(SwitchId switchId, int port, int vlan);

    /**
     * Find flow by SwitchId, input port and output vlan.
     * <p/>
     * IMPORTANT: the method doesn't complete the flow and flow path entities with related path segments!
     */
    Optional<Flow> findBySwitchIdInPortAndOutVlan(SwitchId switchId, int inPort, int outVlan);

    Collection<Flow> findByEndpointWithMultiTableSupport(SwitchId switchId, int port);

    Collection<Flow> findByEndpointSwitch(SwitchId switchId);

    Collection<Flow> findByEndpointSwitchWithMultiTableSupport(SwitchId switchId);

    Collection<Flow> findByEndpointSwitchWithEnabledLldp(SwitchId switchId);

    Collection<Flow> findByEndpointSwitchWithEnabledArp(SwitchId switchId);

    Collection<Flow> findDownFlows();

    Optional<String> getOrCreateFlowGroupId(String flowId);

    void updateStatus(String flowId, FlowStatus flowStatus);

    /**
     * Flow in "IN_PROGRESS" status can be switched to other status only inside flow CRUD handlers topology. All other
     * components must use this method, which guarantee safety such flows status.
     */
    void updateStatusSafe(String flowId, FlowStatus flowStatus);
}
