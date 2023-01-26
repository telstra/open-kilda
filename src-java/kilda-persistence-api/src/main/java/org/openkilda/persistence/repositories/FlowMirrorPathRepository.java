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

package org.openkilda.persistence.repositories;

import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface FlowMirrorPathRepository extends Repository<FlowMirrorPath> {
    boolean exists(PathId mirrorPathId);

    Collection<FlowMirrorPath> findAll();

    Optional<FlowMirrorPath> findById(PathId pathId);

    Map<PathId, FlowMirrorPath> findByIds(Set<PathId> pathIds);

    Collection<FlowMirrorPath> findByEndpointSwitch(SwitchId switchId);

    Collection<FlowMirrorPath> findBySegmentSwitch(SwitchId switchId);

    Map<PathId, FlowMirror> findFlowsMirrorsByPathIds(Set<PathId> pathIds);

    Map<PathId, FlowMirrorPoints> findFlowsMirrorPointsByPathIds(Set<PathId> pathIds);

    void updateStatus(PathId pathId, FlowPathStatus pathStatus);

    Optional<FlowMirrorPath> remove(PathId pathId);
}
