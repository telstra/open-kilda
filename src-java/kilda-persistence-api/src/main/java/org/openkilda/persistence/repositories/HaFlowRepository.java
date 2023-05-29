/* Copyright 2023 Telstra Open Source
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

import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.SwitchId;

import lombok.NonNull;

import java.util.Collection;
import java.util.Optional;

public interface HaFlowRepository extends Repository<HaFlow> {
    Collection<HaFlow> findAll();

    boolean exists(String haFlowId);

    Optional<HaFlow> findById(String haFlowId);

    Collection<HaFlow> findByEndpoint(SwitchId switchId, int port, int vlan, int innerVLan);

    Collection<String> findHaFlowIdsByDiverseGroupId(String diverseGroupId);

    Collection<String> findHaFlowIdsByAffinityGroupId(String affinityGroupId);

    Optional<String> getDiverseHaFlowGroupId(String haFlowId);

    Optional<String> getOrCreateDiverseHaFlowGroupId(String haFlowId);

    void updateStatus(String haFlowId, FlowStatus flowStatus);

    void updateStatus(String haFlowId, FlowStatus flowStatus, String flowStatusInfo);

    void updateAffinityFlowGroupId(@NonNull String haFlowId, String affinityGroupId);

    Optional<HaFlow> remove(String haFlowId);
}
