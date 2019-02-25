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

package org.openkilda.wfm.share.services;

import org.openkilda.model.history.FlowDump;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowHistory;
import org.openkilda.model.history.HistoryLog;
import org.openkilda.model.history.StateLog;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.FlowHistoryRepository;
import org.openkilda.persistence.repositories.history.FlowStateRepository;
import org.openkilda.persistence.repositories.history.HistoryLogRepository;
import org.openkilda.persistence.repositories.history.StateLogRepository;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class HistoryService {
    private final TransactionManager transactionManager;
    private final FlowEventRepository flowEventRepository;
    private final FlowHistoryRepository flowHistoryRepository;
    private final FlowStateRepository flowStateRepository;
    private final HistoryLogRepository historyLogRepository;
    private final StateLogRepository stateLogRepository;

    public HistoryService(PersistenceManager persistenceManager) {
        this(persistenceManager.getTransactionManager(), persistenceManager.getRepositoryFactory());
    }

    public HistoryService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        flowHistoryRepository = repositoryFactory.createFlowHistoryRepository();
        flowStateRepository = repositoryFactory.createFlowStateRepository();
        historyLogRepository = repositoryFactory.createHistoryLogRepository();
        stateLogRepository = repositoryFactory.createStateLogRepository();
    }

    public void store(FlowEvent flowEvent) {
        transactionManager.doInTransaction(() -> flowEventRepository.createOrUpdate(flowEvent));
    }

    /**
     * Writes {@code}FlowHistory{@code} to the DB.
     * Makes edge between the flowHistory and related flowEvent.
     *
     * @param flowHistory the flow history log.
     */
    public void store(FlowHistory flowHistory) {
        transactionManager.doInTransaction(() -> {
            flowHistoryRepository.createOrUpdate(flowHistory);
            Optional<FlowEvent> flowEvents = flowEventRepository.findByTaskId(flowHistory.getTaskId());
            if (flowEvents.isPresent()) {
                historyLogRepository.createOrUpdate(HistoryLog.builder()
                        .flowEvent(flowEvents.get())
                        .flowHistory(flowHistory)
                        .build());
            } else {
                log.warn("Unable to find related FlowEvent by taskId: {}", flowHistory.getTaskId());
            }

        });
    }

    /**
     * Writes {@code}FlowDump{@code} to the DB.
     * Makes edge between the flowDump and related flowEvent.
     *
     * @param flowDump the dump of flow.
     */
    public void store(FlowDump flowDump) {
        transactionManager.doInTransaction(() -> {
            flowStateRepository.createOrUpdate(flowDump);
            Optional<FlowEvent> flowEvents = flowEventRepository.findByTaskId(flowDump.getTaskId());
            if (flowEvents.isPresent()) {
                stateLogRepository.createOrUpdate(StateLog.builder()
                        .flowEvent(flowEvents.get())
                        .flowDump(flowDump)
                        .type(flowDump.getType())
                        .build());
            } else {
                log.warn("Unable to find related FlowEvent by taskId: {}", flowDump.getTaskId());
            }
        });
    }

    public List<FlowEvent> listFlowEvents(String flowId, Instant timeFrom, Instant timeTo) {
        return new ArrayList<>(flowEventRepository.listEventsByFlowIdAndTimeFrame(flowId, timeFrom, timeTo));
    }

    public List<FlowHistory> listFlowHistory(String taskId) {
        return new ArrayList<>(flowHistoryRepository.listFlowHistoryByTaskId(taskId));
    }

    public List<FlowDump> listFlowDump(String taskId) {
        return new ArrayList<>(flowStateRepository.listFlowDumpByTaskId(taskId));
    }
}
