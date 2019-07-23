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

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.FetchStrategy;

import java.util.Collection;
import java.util.Optional;

public interface FlowPathRepository extends Repository<FlowPath> {
    Optional<FlowPath> findById(PathId pathId);

    Optional<FlowPath> findById(PathId pathId, FetchStrategy fetchStrategy);

    Optional<FlowPath> findByFlowIdAndCookie(String flowId, Cookie flowCookie);

    Collection<FlowPath> findByFlowId(String flowId);

    Collection<FlowPath> findByFlowId(String flowId, FetchStrategy fetchStrategy);

    Collection<FlowPath> findByFlowGroupId(String flowGroupId);

    Collection<PathId> findPathIdsByFlowGroupId(String flowGroupId);

    /**
     * Finds paths that starts with passed {@param switchId} switch.
     * NB. This method does not return protected paths with src {@param switchId} switch.
     *
     * @param switchId the src switch
     * @return collection of paths
     */
    Collection<FlowPath> findBySrcSwitch(SwitchId switchId);

    /**
     * Finds paths that have passed {@param switchId} switch in endpoints.
     * NB. This method does not return protected paths with src {@param switchId} switch.
     *
     * @param switchId the endpoint switch
     * @return collection of paths
     */
    Collection<FlowPath> findByEndpointSwitch(SwitchId switchId);

    Collection<FlowPath> findBySegmentSwitch(SwitchId switchId);

    Collection<FlowPath> findBySegmentDestSwitch(SwitchId switchId);

    Collection<FlowPath> findWithPathSegment(SwitchId srcSwitchId, int srcPort,
                                             SwitchId dstSwitchId, int dstPort);

    Collection<FlowPath> findBySegmentEndpoint(SwitchId switchId, int port);

    long getUsedBandwidthBetweenEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort);

    void lockInvolvedSwitches(FlowPath... flowPaths);

    void updateStatus(PathId pathId, FlowPathStatus pathStatus);
}
