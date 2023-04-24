/* Copyright 2021 Telstra Open Source
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

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HaFlowProcessingWithHistorySupportAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C,
        ?, ?>, S, E, C> extends HistoryRecordingAction<T, S, E, C> {
    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    protected final PersistenceManager persistenceManager;
    protected final TransactionManager transactionManager;
    protected final HaFlowRepository haFlowRepository;
    protected final KildaFeatureTogglesRepository featureTogglesRepository;

    protected HaFlowProcessingWithHistorySupportAction(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.haFlowRepository = repositoryFactory.createHaFlowRepository();
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
    }

    protected HaFlow getHaFlow(String haFlowId) {
        return haFlowRepository.findById(haFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("HA-flow %s not found", haFlowId)));
    }
}
