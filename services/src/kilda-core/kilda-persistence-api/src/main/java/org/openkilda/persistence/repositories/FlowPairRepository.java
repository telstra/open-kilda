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

import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * A repository for flow pairs.
 *
 * @deprecated Must be replaced with new model entities: {@link org.openkilda.model.Flow}
 */
@Deprecated
public interface FlowPairRepository extends Repository<FlowPair> {
    boolean exists(String flowId);

    Optional<FlowPair> findById(String flowId);

    Collection<FlowPair> findByGroupId(String flowGroupId);

    Collection<FlowPair> findWithPeriodicPingsEnabled();

    Collection<FlowPair> findByEndpoint(SwitchId switchId, int port);

    Collection<FlowPair> findWithSegmentInPath(SwitchId srcSwitchId, int srcPort,
                                               SwitchId dstSwitchId, int dstPort);

    Set<String> findFlowIdsBySwitch(SwitchId switchId);
}
