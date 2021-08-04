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

package org.openkilda.wfm.share.history.service;

import org.openkilda.model.SwitchId;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventDump;
import org.openkilda.model.history.FlowStatusView;
import org.openkilda.model.history.PortEvent;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.PortEventRepository;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.history.model.PortEventData;
import org.openkilda.wfm.share.mappers.HistoryMapper;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HistoryService {
    private final TransactionManager transactionManager;
    private final FlowEventRepository flowEventRepository;
    private final PortEventRepository portEventRepository;
    private final FlowEventActionRepository flowEventActionRepository;
    private final FlowEventDumpRepository flowEventDumpRepository;

    public HistoryService(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager(TransactionArea.HISTORY);

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        portEventRepository = repositoryFactory.createPortEventRepository();
        flowEventActionRepository = repositoryFactory.createFlowEventActionRepository();
        flowEventDumpRepository = repositoryFactory.createFlowEventDumpRepository();
    }

    /**
     * Save history data into data storage.
     *
     * @param historyHolder holder of history information.
     */
    public void store(FlowHistoryHolder historyHolder) {
        transactionManager.doInTransaction(() -> {
            String taskId = historyHolder.getTaskId();
            if (historyHolder.getFlowEventData() != null) {
                FlowEvent event = HistoryMapper.INSTANCE.map(historyHolder.getFlowEventData());
                event.setTaskId(taskId);
                flowEventRepository.add(event);
            }

            if (historyHolder.getFlowHistoryData() != null) {
                FlowEventAction history = HistoryMapper.INSTANCE.map(historyHolder.getFlowHistoryData());
                history.setTaskId(taskId);
                flowEventActionRepository.add(history);
            }

            if (historyHolder.getFlowDumpData() != null) {
                FlowEventDump dump = HistoryMapper.INSTANCE.map(historyHolder.getFlowDumpData());
                dump.setTaskId(taskId);
                flowEventDumpRepository.add(dump);
            }
        });
    }

    /**
     * Persist the history record.
     */
    public void store(PortEventData data) {
        portEventRepository.add(HistoryMapper.INSTANCE.map(data));
    }

    /**
     * Fetches flow history records by a flow ID and a time period.
     */
    public List<FlowEvent> listFlowEvents(String flowId, Instant timeFrom, Instant timeTo, int maxCount) {
        List<FlowEvent> result = new ArrayList<>();
        transactionManager.doInTransaction(() -> flowEventRepository
                .findByFlowIdAndTimeFrame(flowId, timeFrom, timeTo, maxCount)
                .forEach(entry -> {
                    flowEventRepository.detach(entry);
                    result.add(entry);
                }));
        return result;
    }

    /**
     * Fetches flow status timestamps by a flow ID and a time period.
     */
    public List<FlowStatusView> getFlowStatusTimestamps(String flowId,
                                                        Instant timeFrom, Instant timeTo, int maxCount) {
        return flowEventRepository.findFlowStatusesByFlowIdAndTimeFrame(flowId, timeFrom, timeTo, maxCount);
    }

    /**
     * Fetches port history records.
     */
    public List<PortEvent> listPortHistory(SwitchId switchId, int portNumber, Instant start, Instant end) {
        List<PortEvent> result = new ArrayList<>();
        transactionManager.doInTransaction(() -> portEventRepository
                .findBySwitchIdAndPortNumber(switchId, portNumber, start, end)
                .forEach(entry -> {
                    portEventRepository.detach(entry);
                    result.add(entry);
                }));
        return result;
    }
}
