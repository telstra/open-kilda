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

package org.openkilda.wfm.topology.history.service;

import org.openkilda.messaging.history.FlowEventData;
import org.openkilda.messaging.history.FlowHistoryData;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowHistory;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.FlowHistoryRepository;
import org.openkilda.wfm.share.mappers.FlowEventMapper;
import org.openkilda.wfm.share.mappers.FlowHistoryMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HistoryService {
    private TransactionManager transactionManager;
    private FlowEventRepository flowEventRepository;
    private FlowHistoryRepository flowHistoryRepository;

    public HistoryService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        flowHistoryRepository = repositoryFactory.createFlowHistoryRepository();
    }

    public void store(FlowEventData flowEventData) {
        store(FlowEventMapper.INSTANCE.map(flowEventData));
    }

    public void store(FlowEvent flowEvent) {
        transactionManager.doInTransaction(() -> flowEventRepository.createOrUpdate(flowEvent));
    }

    public void store(FlowHistoryData flowHistoryData) {
        store(FlowHistoryMapper.INSTANCE.map(flowHistoryData));
    }

    public void store(FlowHistory flowHistory) {
        transactionManager.doInTransaction(() -> flowHistoryRepository.createOrUpdate(flowHistory));
    }
}
