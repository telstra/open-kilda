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

import org.openkilda.model.history.FlowEventDump;
import org.openkilda.model.history.FlowEventDump.FlowEventDumpCloner;
import org.openkilda.model.history.FlowEventDump.FlowEventDumpData;
import org.openkilda.persistence.hibernate.HibernatePersistenceImplementation;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEventDump;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;

public class HibernateHistoryFlowEventDumpRepository
        extends HibernateGenericRepository<FlowEventDump, FlowEventDumpData, HibernateFlowEventDump>
        implements FlowEventDumpRepository {
    private final HibernateHistoryFlowEventRepository flowEventRepository;

    public HibernateHistoryFlowEventDumpRepository(
            HibernatePersistenceImplementation implementation,
            HibernateHistoryFlowEventRepository flowEventRepository) {
        super(implementation);
        this.flowEventRepository = flowEventRepository;
    }

    @Override
    protected HibernateFlowEventDump makeEntity(FlowEventDumpData view) {
        HibernateFlowEventDump entity = new HibernateFlowEventDump();
        FlowEventDumpCloner.INSTANCE.copy(view, entity);
        entity.setEvent(flowEventRepository.findEntityByTaskId(view.getTaskId()).orElse(null));
        return entity;
    }

    @Override
    protected FlowEventDumpData doDetach(FlowEventDump model, HibernateFlowEventDump entity) {
        return FlowEventDumpCloner.INSTANCE.deepCopy(entity);
    }
}
