/* Copyright 2022 Telstra Open Source
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
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Optional;

public interface FlowMirrorRepository extends Repository<FlowMirror> {
    boolean exists(String flowMirrorId);

    Collection<FlowMirror> findAll();

    Optional<FlowMirror> findById(String flowMirrorId);

    Optional<FlowMirror> findByEgressEndpoint(SwitchId switchId, int port, int outerVlan, int innerVlan);

    Collection<FlowMirror> findByEgressSwitchIdAndPort(SwitchId switchId, int port);

    void updateStatus(String flowMirrorId, FlowPathStatus status);

    Optional<FlowMirror> remove(String flowMirrorId);
}
