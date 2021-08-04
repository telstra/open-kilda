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

package org.openkilda.persistence.hibernate.repositories;

import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventAction.FlowEventActionCloner;
import org.openkilda.model.history.FlowEventAction.FlowEventActionData;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEventAction;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.hibernate.SessionFactory;

import java.util.function.Supplier;

public class HibernateHistoryFlowEventActionRepository
        extends HibernateGenericRepository<FlowEventAction, FlowEventActionData, HibernateFlowEventAction>
        implements FlowEventActionRepository {
    private final HibernateHistoryFlowEventRepository flowEventRepository;

    public HibernateHistoryFlowEventActionRepository(
            TransactionManager transactionManager, Supplier<SessionFactory> factorySupplier,
            HibernateHistoryFlowEventRepository flowEventRepository) {
        super(transactionManager, factorySupplier);
        this.flowEventRepository = flowEventRepository;
    }

    @Override
    protected HibernateFlowEventAction makeEntity(FlowEventActionData view) {
        HibernateFlowEventAction entity = new HibernateFlowEventAction();
        FlowEventActionCloner.INSTANCE.copy(view, entity);
        entity.setEvent(flowEventRepository.findEntityByTaskId(view.getTaskId()).orElse(null));
        return entity;
    }

    @Override
    protected FlowEventActionData doDetach(FlowEventAction model, HibernateFlowEventAction entity) {
        return FlowEventActionCloner.INSTANCE.deepCopy(entity);
    }
}
