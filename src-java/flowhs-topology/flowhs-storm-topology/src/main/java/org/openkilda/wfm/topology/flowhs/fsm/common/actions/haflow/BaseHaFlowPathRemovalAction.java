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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow;

import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

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

    protected void removeFlowPaths(HaPathIdsPair haPathIdsPair,
                                   HistoryUpdateCarrier carrier, String correlationId) {
        if (haPathIdsPair == null) {
            return;
        }
        FlowHistoryService flowHistoryService = FlowHistoryService.using(carrier);

        for (PathId subPathId : haPathIdsPair.getAllSubPathIds()) {
            if (subPathId != null) {
                FlowPath oldSubPath = flowPathRepository.remove(subPathId).orElse(null);
                if (oldSubPath != null) {
                    log.debug("Removed HA-flow sub path {}", oldSubPath);
                    updateIslsForFlowPath(oldSubPath);
                    flowHistoryService.save(HaFlowHistory.of(correlationId)
                                    .withAction("Remove an HA-flow sub path")
                                    .withDescription(String.format("%s with ID %s has been removed",
                                            oldSubPath.getClass().getSimpleName(), oldSubPath.getPathId())));
                }
            }
        }

        for (PathId haFlowPathId : haPathIdsPair.getAllHaFlowPathIds()) {
            if (haFlowPathId != null) {
                HaFlowPath oldPath = haFlowPathRepository.remove(haFlowPathId).orElse(null);
                if (oldPath != null) {
                    log.debug("Removed HA-flow path {}", oldPath);
                    flowHistoryService.save(HaFlowHistory.of(correlationId)
                                    .withAction("Remove an HA-flow path")
                                    .withDescription(String.format("%s with ID %s has been removed",
                                            oldPath.getClass().getSimpleName(), oldPath.getHaPathId()))
                                    .withHaFlowId(oldPath.getHaFlowId()));
                }
            }
        }
    }

    protected void removeRejectedPaths(Collection<PathId> subPathIds, Collection<PathId> haFlowPathIds,
                                       HistoryUpdateCarrier carrier, String correlationId) {
        FlowHistoryService flowHistoryService = FlowHistoryService.using(carrier);

        for (PathId subPathId : subPathIds) {
            flowPathRepository.remove(subPathId)
                    .ifPresent(subPath -> {
                        updateIslsForFlowPath(subPath);
                        flowHistoryService.save(HaFlowHistory.of(correlationId)
                                .withAction("Remove a rejected path")
                                .withDescription(String.format("%s with ID %s has been removed",
                                        subPath.getClass().getSimpleName(), subPath.getPathId()))
                                .withHaFlowId(subPath.getHaFlowId()));
                    });
        }
        for (PathId haFlowPathId : haFlowPathIds) {
            haFlowPathRepository.remove(haFlowPathId)
                    .ifPresent(haFlowPath -> {
                        flowHistoryService.save(HaFlowHistory.of(correlationId)
                                .withAction("Remove a rejected path")
                                .withDescription(String.format("%s with ID %s has been removed",
                                        haFlowPath.getClass().getSimpleName(), haFlowPath.getHaPathId()))
                                .withHaFlowId(haFlowPath.getHaFlowId()));
                    });
        }
    }
}
