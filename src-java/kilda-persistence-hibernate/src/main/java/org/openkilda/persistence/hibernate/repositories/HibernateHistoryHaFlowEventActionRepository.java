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


package org.openkilda.persistence.hibernate.repositories;

import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventAction.HaFlowEventActionCloner;
import org.openkilda.model.history.HaFlowEventAction.HaFlowEventActionData;
import org.openkilda.persistence.hibernate.HibernatePersistenceImplementation;
import org.openkilda.persistence.hibernate.entities.history.HibernateHaFlowEventAction;
import org.openkilda.persistence.repositories.history.HaFlowEventActionRepository;

public class HibernateHistoryHaFlowEventActionRepository
        extends HibernateGenericRepository<HaFlowEventAction, HaFlowEventActionData, HibernateHaFlowEventAction>
        implements HaFlowEventActionRepository {

    private final HibernateHistoryHaFlowEventRepository haFlowEventRepository;

    public HibernateHistoryHaFlowEventActionRepository(HibernatePersistenceImplementation implementation,
                                                       HibernateHistoryHaFlowEventRepository haFlowEventRepository) {
        super(implementation);
        this.haFlowEventRepository = haFlowEventRepository;
    }

    @Override
    protected HibernateHaFlowEventAction makeEntity(HaFlowEventActionData view) {
        HibernateHaFlowEventAction entity = new HibernateHaFlowEventAction();
        HaFlowEventActionCloner.INSTANCE.copy(view, entity);
        entity.setHaFlowEvent(haFlowEventRepository.findEntityByTaskId(view.getTaskId()).orElse(null));
        return entity;
    }

    @Override
    protected HaFlowEventActionData doDetach(HaFlowEventAction model, HibernateHaFlowEventAction entity) {
        return HaFlowEventActionCloner.INSTANCE.deepCopy(entity);
    }
}
