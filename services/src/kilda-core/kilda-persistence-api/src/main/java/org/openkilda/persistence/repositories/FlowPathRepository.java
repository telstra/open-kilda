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
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Optional;

public interface FlowPathRepository extends Repository<FlowPath> {
    Optional<FlowPath> findById(PathId pathId);

    Optional<FlowPath> findByFlowIdAndCookie(String flowId, Cookie flowCookie);

    Collection<FlowPath> findByFlowId(String flowId);

    Collection<FlowPath> findByFlowGroupId(String flowGroupId);

    Collection<FlowPath> findBySrcSwitchId(SwitchId switchId);

    Collection<PathSegment> findPathSegmentsBySrcSwitchId(SwitchId switchId);

    Collection<FlowPath> findBySegmentSrcSwitchId(SwitchId switchId);

    Collection<PathSegment> findPathSegmentsByDestSwitchId(SwitchId switchId);

    Collection<FlowPath> findBySegmentDestSwitchId(SwitchId switchId);

    long getUsedBandwidthBetweenEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort);
}
