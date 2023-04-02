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

import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Optional;

public interface MirrorGroupRepository extends Repository<MirrorGroup> {
    Collection<MirrorGroup> findAll();

    /**
     * Find group by Path Id.
     *
     * @param pathId path ID
     * @return a collection of {@link MirrorGroup}
     */
    Collection<MirrorGroup> findByPathId(PathId pathId);

    /**
     * Find group by Switch Id.
     *
     * @param switchId switch ID
     * @return a collection of {@link MirrorGroup}
     */
    Collection<MirrorGroup> findBySwitchId(SwitchId switchId);

    /**
     * Find group by Group Id.
     *
     * @param groupId group ID
     * @return a collection of {@link MirrorGroup}
     */
    Optional<MirrorGroup> findByGroupIdAndSwitchId(GroupId groupId, SwitchId switchId);

    /**
     * Find group by Path Id and Switch Id.
     *
     * @param pathId path ID
     * @param switchId switch ID
     * @return a collection of {@link MirrorGroup}
     */
    Optional<MirrorGroup> findByPathIdAndSwitchId(PathId pathId, SwitchId switchId);

    boolean exists(SwitchId switchId, GroupId groupId);

    /**
     * Find a group id which is not assigned to any flow.
     *
     * @param switchId       the switch defines where the group is applied on.
     * @param lowestGroupId the lowest value for a potential group id.
     * @param highestGroupId the highest value for a potential group id.
     * @return a meter id or {@link Optional#empty()} if no meter available.
     */
    Optional<GroupId> findFirstUnassignedGroupId(SwitchId switchId, GroupId lowestGroupId, GroupId highestGroupId);
}
