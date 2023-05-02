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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * A base for action classes that remove flow paths.
 */
@Slf4j
public abstract class BaseHaFlowPathRemovalAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E,
        C> extends BaseFlowPathRemovalAction<T, S, E, C> {
    protected final HaFlowPathRepository haFlowPathRepository;

    protected BaseHaFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        haFlowPathRepository = persistenceManager.getRepositoryFactory().createHaFlowPathRepository();
    }

    protected void removeFlowPaths(HaPathIdsPair haPathIdsPair) {
        if (haPathIdsPair == null) {
            return;
        }

        for (PathId subPathId : haPathIdsPair.getAllSubPathIds()) {
            if (subPathId != null) {
                FlowPath oldSubPath = flowPathRepository.remove(subPathId).orElse(null);
                if (oldSubPath != null) {
                    log.debug("Removed ha-flow sub path {}", oldSubPath);
                    updateIslsForFlowPath(oldSubPath);
                    // TODO save info about removed paths into history https://github.com/telstra/open-kilda/issues/5169
                }
            }
        }

        for (PathId haFlowPathId : haPathIdsPair.getAllHaFlowPathIds()) {
            if (haFlowPathId != null) {
                HaFlowPath oldPath = haFlowPathRepository.remove(haFlowPathId).orElse(null);
                if (oldPath != null) {
                    log.debug("Removed ha-flow path {}", oldPath);
                    // TODO save info about removed paths into history https://github.com/telstra/open-kilda/issues/5169
                }
            }
        }
    }

    protected void removeRejectedFlowPaths(Collection<PathId> rejectedPathIds) {
        for (PathId pathId : rejectedPathIds) {
            flowPathRepository.remove(pathId)
                    .ifPresent(subPath -> {
                        updateIslsForFlowPath(subPath);
                        // TODO save info about removed paths into history
                        // https://github.com/telstra/open-kilda/issues/5169
                    });
            haFlowPathRepository.remove(pathId)
                    .ifPresent(haFlowPath -> {
                        // TODO save info about removed paths into history
                        // https://github.com/telstra/open-kilda/issues/5169
                    });
        }
    }
}
