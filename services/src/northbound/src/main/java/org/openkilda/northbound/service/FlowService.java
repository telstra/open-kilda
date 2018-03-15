/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.service;

import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.info.flow.FlowInfoData;

import java.util.List;

/**
 * FlowService is for operations on flows, primarily against the Flow Topology.
 */
public interface FlowService extends BasicService {
    /**
     * Creates flow.
     *
     * @param flow          flow
     * @return created flow
     */
    FlowPayload createFlow(final FlowPayload flow);

    /**
     * Deletes flow.
     *
     * @param id            flow id
     * @return deleted flow
     */
    FlowPayload deleteFlow(final String id);

    /**
     * Updates flow.
     *
     * @param flow          flow
     * @return updated flow
     */
    FlowPayload updateFlow(final FlowPayload flow);

    /**
     * Gets flow by id.
     *
     * @param id            flow id
     * @return flow
     */
    FlowPayload getFlow(final String id);

    /**
     * Gets all the flows.
     *
     * @return the list of all flows with specified status
     */
    List<FlowPayload> getFlows();

    /**
     * Deletes all flows. Primarily this is a combination of getFlows and deleteFlow.
     * This should be called with care ..
     *
     * @return the list of all deleted flows
     */
    List<FlowPayload> deleteFlows();

    /**
     * Gets flow status by id.
     *
     * @param id            flow id
     * @return flow status
     */
    FlowIdStatusPayload statusFlow(final String id);

    /**
     * Gets flow path by id.
     *
     * @param id            flow id
     * @return Flow path
     */
    FlowPathPayload pathFlow(final String id);

    /**
     * Use this to push flows that may not be in the database / caches but they should be
     *
     * @param externalFlows   the list of flows to push.
     * @return
     */
    BatchResults pushFlows(final List<FlowInfoData> externalFlows);

    /**
     * Use this to unpush flows .. ie undo a push
     *
     * @param externalFlows   the list of flows to unpush.
     * @return
     */
    BatchResults unpushFlows(final List<FlowInfoData> externalFlows);


    /**
     * Performs rerouting of specific flow.
     *
     * @param flowId id of flow to be rerouted.
     * @return updated flow path information.
     */
    FlowPathPayload rerouteFlow(final String flowId);


    /**
     * Sync the FlowCache in the flow topology (in case it is out of sync.
     *
     * @return updated flow path information.
     */
    FlowCacheSyncResults syncFlowCache();
}
