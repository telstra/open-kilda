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

package org.openkilda.persistence.repositories;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface FlowRepository extends Repository<Flow> {
    boolean exists(String flowId);

    Collection<Flow> findById(String flowId);

    Collection<Flow> findByGroupId(String flowGroupId);

    Optional<Flow> findByIdAndCookie(String flowId, long cookie);

    Optional<FlowPair> findFlowPairById(String flowId);

    Collection<FlowPair> findAllFlowPairs();

    Collection<FlowPair> findFlowPairsWithPeriodicPingsEnabled();

    Collection<Flow> findFlowIdsByEndpoint(SwitchId switchId, int port);

    Collection<String> findActiveFlowIdsWithPortInPath(SwitchId switchId, int port);

    Collection<String> findDownFlowIds();

    Collection<Flow> findBySrcSwitchId(SwitchId switchId);

    Collection<Flow> findByDstSwitchId(SwitchId switchId);

    void createOrUpdate(FlowPair flowPair);

    void delete(FlowPair flowPair);

    Optional<String> getOrCreateFlowGroupId(String flowId);

    Collection<FlowPair> findAllFlowPairsWithSegment(SwitchId srcSwitchId, int srcPort,
                                                     SwitchId dstSwitchId, int dstPort);

    Set<String> findFlowIdsBySwitch(SwitchId switchId);

    Collection<FlowPair> findFlowPairsByGroupId(String flowGroupId);
}
