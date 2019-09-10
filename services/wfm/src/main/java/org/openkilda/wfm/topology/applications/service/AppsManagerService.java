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

package org.openkilda.wfm.topology.applications.service;

import static java.lang.String.format;

import org.openkilda.applications.command.apps.CreateExclusion;
import org.openkilda.applications.command.apps.RemoveExclusion;
import org.openkilda.applications.info.apps.CreateExclusionResult;
import org.openkilda.applications.info.apps.RemoveExclusionResult;
import org.openkilda.messaging.command.apps.FlowAddAppRequest;
import org.openkilda.messaging.command.apps.FlowRemoveAppRequest;
import org.openkilda.messaging.info.apps.FlowAppsResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.error.FlowNotFoundException;

import java.util.Objects;

public class AppsManagerService {

    private FlowRepository flowRepository;

    public AppsManagerService(PersistenceManager persistenceManager) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    /**
     * Get applications for flow.
     */
    public FlowAppsResponse getEnabledFlowApplications(String flowId) throws FlowNotFoundException {
        Flow flow = getFlow(flowId);
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    /**
     * Add application for flow endpoint or endpoints.
     */
    public FlowAppsResponse addFlowApplication(FlowAddAppRequest payload) throws FlowNotFoundException {
        Flow flow = getFlow(payload.getFlowId());
        validateEndpoint(flow, payload.getSwitchId(), payload.getPortNumber(), payload.getVlanId());
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    /**
     * Remove application for flow endpoint or endpoints.
     */
    public FlowAppsResponse removeFlowApplication(FlowRemoveAppRequest payload) throws FlowNotFoundException {
        Flow flow = getFlow(payload.getFlowId());
        validateEndpoint(flow, payload.getSwitchId(), payload.getPortNumber(), payload.getVlanId());
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    private Flow getFlow(String flowId) throws FlowNotFoundException {
        return flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    private void validateEndpoint(Flow flow, SwitchId switchId, Integer port, Integer vlan) {
        if (switchId == null && port == null && vlan == null) {
            return;
        }

        if (flow.getSrcSwitch().getSwitchId().equals(switchId)
                && Objects.equals(flow.getSrcPort(), port)
                && Objects.equals(flow.getSrcVlan(), vlan)) {
            return;
        }

        if (flow.getDestSwitch().getSwitchId().equals(switchId)
                && Objects.equals(flow.getDestPort(), port)
                && Objects.equals(flow.getDestVlan(), vlan)) {
            return;
        }

        throw new IllegalArgumentException(
                format("Endpoint {switch_id = %s, port_number = %d, vlan_id = %d} is not a flow endpoint.",
                        switchId, port, vlan));
    }

    /**
     * Create exclusion for the flow.
     */
    public CreateExclusionResult processCreateExclusion(CreateExclusion payload) throws FlowNotFoundException {
        return CreateExclusionResult.builder()
                .flowId(payload.getFlowId())
                .endpoint(payload.getEndpoint())
                .application(payload.getApplication())
                .exclusion(payload.getExclusion())
                .success(false)
                .build();
    }

    /**
     * Remove exclusion for the flow.
     */
    public RemoveExclusionResult processRemoveExclusion(RemoveExclusion payload) throws FlowNotFoundException {
        return RemoveExclusionResult.builder()
                .flowId(payload.getFlowId())
                .endpoint(payload.getEndpoint())
                .application(payload.getApplication())
                .exclusion(payload.getExclusion())
                .success(false)
                .build();
    }
}
