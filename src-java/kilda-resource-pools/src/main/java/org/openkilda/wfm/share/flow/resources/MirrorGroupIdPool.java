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

package org.openkilda.wfm.share.flow.resources;

import static java.lang.String.format;

import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionRequired;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The resource pool is responsible for group id de-/allocation.
 */
@Slf4j
public class MirrorGroupIdPool {
    private final TransactionManager transactionManager;
    private final MirrorGroupRepository mirrorGroupRepository;

    private final GroupId minGroupId;
    private final GroupId maxGroupId;
    private final int poolSize;

    private final Map<SwitchId, GroupId> nextGroupIds = new HashMap<>();

    public MirrorGroupIdPool(PersistenceManager persistenceManager,
                             GroupId minGroupId, GroupId maxGroupId, int poolSize) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        mirrorGroupRepository = repositoryFactory.createMirrorGroupRepository();

        this.minGroupId = minGroupId;
        this.maxGroupId = maxGroupId;
        this.poolSize = poolSize <= 0 ? 1 : poolSize;
    }

    /**
     * Allocates a group id for the flow path.
     */
    @TransactionRequired
    public MirrorGroup allocate(SwitchId switchId, String flowId, PathId pathId,
                                MirrorGroupType type, MirrorDirection direction) {
        GroupId nextGroupId = nextGroupIds.get(switchId);
        if (nextGroupId != null && nextGroupId.getValue() > 0) {
            if (nextGroupId.compareTo(maxGroupId) <= 0 && !mirrorGroupRepository.exists(switchId, nextGroupId)) {
                MirrorGroup mirrorGroup = addMirrorGroup(flowId, pathId, switchId, nextGroupId, type, direction);
                nextGroupIds.put(switchId, new GroupId(nextGroupId.getValue() + 1));
                return mirrorGroup;
            } else {
                nextGroupIds.remove(switchId);
            }
        }
        // The pool requires (re-)initialization.
        if (!nextGroupIds.containsKey(switchId)) {
            long numOfPools = (maxGroupId.getValue() - minGroupId.getValue()) / poolSize;
            if (numOfPools > 1) {
                long poolToTake = Math.abs(new Random().nextInt()) % numOfPools;
                Optional<GroupId> availableGroupId = mirrorGroupRepository.findFirstUnassignedGroupId(switchId,
                        new GroupId(minGroupId.getValue() + poolToTake * poolSize),
                        new GroupId(minGroupId.getValue() + (poolToTake + 1) * poolSize - 1));
                if (availableGroupId.isPresent()) {
                    nextGroupId = availableGroupId.get();
                    MirrorGroup mirrorGroup = addMirrorGroup(flowId, pathId, switchId, nextGroupId, type, direction);
                    nextGroupIds.put(switchId, new GroupId(nextGroupId.getValue() + 1));
                    return mirrorGroup;
                }
            }
            // The pool requires full scan.
            nextGroupId = new GroupId(-1);
            nextGroupIds.put(switchId, nextGroupId);
        }
        if (nextGroupId != null && nextGroupId.getValue() == -1) {
            Optional<GroupId> availableMeter = mirrorGroupRepository.findFirstUnassignedGroupId(switchId,
                    minGroupId, maxGroupId);
            if (availableMeter.isPresent()) {
                nextGroupId = availableMeter.get();
                MirrorGroup mirrorGroup = addMirrorGroup(flowId, pathId, switchId, nextGroupId, type, direction);
                nextGroupIds.put(switchId, new GroupId(nextGroupId.getValue() + 1));
                return mirrorGroup;
            }
        }
        throw new ResourceNotAvailableException(format("No group id available for switch %s", switchId));
    }

    private MirrorGroup addMirrorGroup(String flowId, PathId pathId, SwitchId switchId, GroupId groupId,
                                       MirrorGroupType type, MirrorDirection direction) {
        MirrorGroup mirrorGroup = MirrorGroup.builder()
                .groupId(groupId)
                .switchId(switchId)
                .flowId(flowId)
                .pathId(pathId)
                .mirrorGroupType(type)
                .mirrorDirection(direction)
                .build();
        mirrorGroupRepository.add(mirrorGroup);
        return mirrorGroup;
    }

    /**
     * Deallocates a group id.
     */
    public void deallocate(PathId pathId, SwitchId switchId) {
        transactionManager.doInTransaction(() -> mirrorGroupRepository.findByPathIdAndSwitchId(pathId, switchId)
                .ifPresent(mirrorGroupRepository::remove));
    }
}
