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
import org.openkilda.model.history.HaFlowEvent;
import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.model.history.PortEvent;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.HaFlowEventActionRepository;
import org.openkilda.persistence.repositories.history.HaFlowEventDumpRepository;
import org.openkilda.persistence.repositories.history.HaFlowEventRepository;
import org.openkilda.persistence.repositories.history.PortEventRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.history.model.PortEventData;
import org.openkilda.wfm.share.mappers.HaFlowHistoryMapper;
import org.openkilda.wfm.share.mappers.HistoryMapper;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public class HistoryService {
    private final TransactionManager transactionManager;
    private final FlowEventRepository flowEventRepository;
    private final PortEventRepository portEventRepository;
    private final FlowEventActionRepository flowEventActionRepository;
    private final FlowEventDumpRepository flowEventDumpRepository;
    private final HaFlowEventRepository haFlowEventRepository;
    private final HaFlowEventActionRepository haFlowEventActionRepository;
    private final HaFlowEventDumpRepository haFlowEventDumpRepository;

    public HistoryService(PersistenceManager persistenceManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        portEventRepository = repositoryFactory.createPortEventRepository();
        flowEventActionRepository = repositoryFactory.createFlowEventActionRepository();
        flowEventDumpRepository = repositoryFactory.createFlowEventDumpRepository();

        haFlowEventRepository = repositoryFactory.createHaFlowEventRepository();
        haFlowEventActionRepository = repositoryFactory.createHaFlowEventActionRepository();
        haFlowEventDumpRepository = repositoryFactory.createHaFlowEventDumpRepository();

        transactionManager = persistenceManager.getTransactionManager(
                flowEventRepository, portEventRepository, flowEventActionRepository, flowEventDumpRepository,
                haFlowEventRepository, haFlowEventActionRepository, haFlowEventDumpRepository);
    }

    private void storeHaFlowHistory(FlowHistoryHolder historyHolder) {
        HaFlowHistoryMapper mapper = HaFlowHistoryMapper.INSTANCE;
        transactionManager.doInTransaction(() -> {
            String taskId = historyHolder.getTaskId();
            if (historyHolder.getHaFlowEventData() != null) {
                HaFlowEvent event = mapper.createHaFlowEvent(historyHolder.getHaFlowEventData(), taskId);
                haFlowEventRepository.add(event);
            }

            if (historyHolder.getHaFlowHistoryData() != null) {
                HaFlowEventAction history = mapper.createHaFlowEventAction(historyHolder.getHaFlowHistoryData(),
                        taskId);
                haFlowEventActionRepository.add(history);
            }

            if (historyHolder.getHaFlowDumpData() != null) {
                HaFlowEventDump dump = new HaFlowEventDump(
                        mapper.createHaFlowEventDump(historyHolder.getHaFlowDumpData()));
                haFlowEventDumpRepository.add(dump);
            }
        });
    }

    private void storeSimpleFlowHistory(FlowHistoryHolder historyHolder) {
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
     * Persists supported non-empty fields of the history holder.
     *
     * @param historyHolder holder of history information.
     */
    public void store(FlowHistoryHolder historyHolder) {
        if (historyHolder.isHaFlowHistory()) {
            storeHaFlowHistory(historyHolder);
        } else {
            storeSimpleFlowHistory(historyHolder);
        }
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
     * Retrieves HA-flow history data from repository.
     * @param haFlowId target HA-flow ID
     * @param timeFrom get history starting from this time
     * @param timeTo get history ending by this time
     * @param maxCount get no more than this number of entries
     * @return a list of HA-flow events
     */
    public List<HaFlowEvent> getHaFlowHistoryEvents(String haFlowId, Instant timeFrom, Instant timeTo, int maxCount) {
        List<HaFlowEvent> result = new LinkedList<>();
        transactionManager.doInTransaction(() -> haFlowEventRepository
                .findByHaFlowIdAndTimeFrame(haFlowId, timeFrom, timeTo, maxCount))
                .forEach(haFlowEvent -> {
                    haFlowEventRepository.detach(haFlowEvent);
                    result.add(haFlowEvent);
                });
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
