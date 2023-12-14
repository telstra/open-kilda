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

import org.openkilda.model.history.HaFlowEvent;
import org.openkilda.model.history.HaFlowEvent.HaFlowEventCloner;
import org.openkilda.model.history.HaFlowEvent.HaFlowEventData;
import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventAction.HaFlowEventActionCloner;
import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpCloner;
import org.openkilda.persistence.hibernate.HibernatePersistenceImplementation;
import org.openkilda.persistence.hibernate.entities.history.HibernateHaFlowEvent;
import org.openkilda.persistence.hibernate.entities.history.HibernateHaFlowEventAction;
import org.openkilda.persistence.hibernate.entities.history.HibernateHaFlowEventDump;
import org.openkilda.persistence.hibernate.utils.UniqueKeyUtil;
import org.openkilda.persistence.repositories.history.HaFlowEventRepository;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

@Slf4j
public class HibernateHistoryHaFlowEventRepository
        extends HibernateGenericRepository<HaFlowEvent, HaFlowEventData, HibernateHaFlowEvent>
        implements HaFlowEventRepository {

    public HibernateHistoryHaFlowEventRepository(HibernatePersistenceImplementation implementation) {
        super(implementation);
    }

    public Optional<HibernateHaFlowEvent> findEntityByTaskId(String taskId) {
        return UniqueKeyUtil.findEntityUsingTaskIdUniqueKey(taskId, getSession(), HibernateHaFlowEvent.class);
    }

    @Override
    public List<HaFlowEvent> findByHaFlowIdAndTimeFrame(String haFlowId,
                                                        Instant timeFrom, Instant timeTo, int maxCount) {
        List<HaFlowEvent> results = getTransactionManager().doInTransaction(
                () -> fetch(haFlowId, timeFrom, timeTo, maxCount).stream()
                        .map(HaFlowEvent::new)
                        .collect(Collectors.toList()));
        // fetch does the ordering [1,2,3,4,5] and limit to maxCount (let's say top 3) [1,2,3]
        // then we reverse the collection [3,2,1].
        // This is different from having the opposite ordering in the query: order [5,4,3,2,1] and top 3: [5,4,3]
        Collections.reverse(results);
        log.info("HaFlowEvent repo: fetched {} events", results.size());
        return results;
    }

    @Override
    protected HibernateHaFlowEvent makeEntity(HaFlowEventData view) {
        HibernateHaFlowEvent entity = new HibernateHaFlowEvent();
        HaFlowEventCloner.INSTANCE.copyWithoutRecordsAndDumps(view, entity);

        for (HaFlowEventAction action : view.getEventActions()) {
            HibernateHaFlowEventAction actionEntity = new HibernateHaFlowEventAction();
            HaFlowEventActionCloner.INSTANCE.copy(action.getData(), actionEntity);
            entity.linkAction(actionEntity);
        }

        for (HaFlowEventDump dump : view.getEventDumps()) {
            HibernateHaFlowEventDump dumpEntity = new HibernateHaFlowEventDump();
            HaFlowEventDumpCloner.INSTANCE.copy(dump.getData(), dumpEntity);
            entity.linkDump(dumpEntity);
        }

        return entity;
    }

    @Override
    protected HaFlowEventData doDetach(HaFlowEvent model, HibernateHaFlowEvent entity) {
        return HaFlowEventCloner.INSTANCE.deepCopy(entity);
    }

    // TODO decide whether to extract this code to some util class because this is a duplicate
    private List<HibernateHaFlowEvent> fetch(String haFlowId, Instant timeFrom, Instant timeTo, int maxCount) {
        CriteriaBuilder builder = getSession().getCriteriaBuilder();
        CriteriaQuery<HibernateHaFlowEvent> query = builder.createQuery(HibernateHaFlowEvent.class);
        Root<HibernateHaFlowEvent> root = query.from(HibernateHaFlowEvent.class);
        query.select(root);
        query.where(makeQueryFilter(root, haFlowId, timeFrom, timeTo).toArray(new Predicate[0]));
        query.orderBy(
                builder.desc(root.get("timestamp")),
                builder.desc(root.get("haFlowId")));
        return getSession().createQuery(query).setMaxResults(maxCount).getResultList();
    }

    private List<Predicate> makeQueryFilter(
            Root<HibernateHaFlowEvent> root, String haFlowId, Instant timeFrom, Instant timeTo) {
        List<Predicate> filters = new ArrayList<>(3);
        CriteriaBuilder builder = getSession().getCriteriaBuilder();
        filters.add(builder.equal(root.get("haFlowId"), haFlowId));
        if (timeFrom != null) {
            filters.add(builder.greaterThanOrEqualTo(root.get("timestamp"), timeFrom));
        }
        if (timeTo != null) {
            filters.add(builder.lessThan(root.get("timestamp"), timeTo));
        }
        return filters;
    }
}
