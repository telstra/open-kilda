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

import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpCloner;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpData;
import org.openkilda.persistence.hibernate.HibernatePersistenceImplementation;
import org.openkilda.persistence.hibernate.entities.history.HibernateHaFlowEventDump;
import org.openkilda.persistence.repositories.history.HaFlowEventDumpRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HibernateHistoryHaFlowEventDumpRepository
        extends HibernateGenericRepository<HaFlowEventDump, HaFlowEventDumpData, HibernateHaFlowEventDump>
        implements HaFlowEventDumpRepository {

    private final HibernateHistoryHaFlowEventRepository haFlowEventRepository;

    public HibernateHistoryHaFlowEventDumpRepository(HibernatePersistenceImplementation implementation,
                                                     HibernateHistoryHaFlowEventRepository haFlowEventRepository) {
        super(implementation);
        this.haFlowEventRepository = haFlowEventRepository;
    }

    @Override
    protected HibernateHaFlowEventDump makeEntity(HaFlowEventDumpData view) {
        if (view == null) {
            log.info("makeEntity is called, but the view is null.");
            return new HibernateHaFlowEventDump();
        }

        HibernateHaFlowEventDump entity = new HibernateHaFlowEventDump();
        HaFlowEventDumpCloner.INSTANCE.copy(view, entity);
        entity.setHaFlowEvent(haFlowEventRepository.findEntityByTaskId(view.getTaskId()).orElse(null));
        return entity;
    }

    @Override
    protected HaFlowEventDumpData doDetach(HaFlowEventDump model, HibernateHaFlowEventDump entity) {
        return HaFlowEventDumpCloner.INSTANCE.deepCopy(entity);
    }
}
