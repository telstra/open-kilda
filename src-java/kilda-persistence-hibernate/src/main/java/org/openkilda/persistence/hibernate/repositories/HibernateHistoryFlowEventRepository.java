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

import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowEvent.FlowEventCloner;
import org.openkilda.model.history.FlowEvent.FlowEventData;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventAction.FlowEventActionCloner;
import org.openkilda.model.history.FlowEventDump;
import org.openkilda.model.history.FlowEventDump.FlowEventDumpCloner;
import org.openkilda.model.history.FlowStatusView;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.hibernate.HibernatePersistenceImplementation;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEvent;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEventAction;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEventDump;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEvent_;
import org.openkilda.persistence.repositories.history.FlowEventRepository;

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

public class HibernateHistoryFlowEventRepository
        extends HibernateGenericRepository<FlowEvent, FlowEventData, HibernateFlowEvent>
        implements FlowEventRepository {
    public HibernateHistoryFlowEventRepository(HibernatePersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    public boolean existsByTaskId(String taskId) {
        return getTransactionManager().doInTransaction(() -> findEntityByTaskId(taskId).isPresent());
    }

    @Override
    public Optional<FlowEvent> findByTaskId(String taskId) {
        return getTransactionManager().doInTransaction(() -> findEntityByTaskId(taskId).map(FlowEvent::new));
    }

    @Override
    public List<FlowEvent> findByFlowIdAndTimeFrame(
            String flowId, Instant timeFrom, Instant timeTo, int maxCount) {
        List<FlowEvent> results = getTransactionManager().doInTransaction(
                () -> fetch(flowId, timeFrom, timeTo, maxCount).stream()
                .map(FlowEvent::new)
                .collect(Collectors.toList()));
        Collections.reverse(results);
        return results;
    }

    @Override
    public List<FlowStatusView> findFlowStatusesByFlowIdAndTimeFrame(
            String flowId, Instant timeFrom, Instant timeTo, int maxCount) {
        List<FlowStatusView> results = getTransactionManager().doInTransaction(
                () -> fetch(flowId, timeFrom, timeTo, maxCount).stream()
                .flatMap(entry -> entry.getActions().stream())
                .map(this::extractStatusUpdates)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList()));
        Collections.reverse(results);
        return results;
    }

    /**
     * Fetch and return hibernate {@link HibernateFlowEvent} entity, dedicated to use by others hibernate repositories.
     * NOTE: taskId field has no index, but taskIdUniqueKey has, so to find FlowEvent by taskId we will use unique key
     */
    public Optional<HibernateFlowEvent> findEntityByTaskId(String taskId) {
        String taskIdKey = HibernateFlowEvent.makeTaskIdUniqueKey(taskId);
        CriteriaBuilder builder = getSession().getCriteriaBuilder();
        CriteriaQuery<HibernateFlowEvent> query = builder.createQuery(HibernateFlowEvent.class);
        Root<HibernateFlowEvent> root = query.from(HibernateFlowEvent.class);
        query.select(root);
        query.where(builder.equal(root.get(HibernateFlowEvent_.taskIdUniqueKey), taskIdKey));
        List<HibernateFlowEvent> results = getSession().createQuery(query).getResultList();

        if (1 < results.size()) {
            throw new PersistenceException(String.format(
                    "Unique constraint violation on field %s of %s. %s is %s",
                    HibernateFlowEvent_.taskId, HibernateFlowEvent.class.getName(),
                    HibernateFlowEvent_.taskIdUniqueKey, taskIdKey));
        }
        if (!results.isEmpty()) {
            return Optional.of(results.get(0));
        }
        return Optional.empty();
    }

    @Override
    protected HibernateFlowEvent makeEntity(FlowEventData view) {
        HibernateFlowEvent entity = new HibernateFlowEvent();
        FlowEventCloner.INSTANCE.copyWithoutRecordsAndDumps(view, entity);

        for (FlowEventAction entry : view.getEventActions()) {
            HibernateFlowEventAction action = new HibernateFlowEventAction();
            FlowEventActionCloner.INSTANCE.copy(entry.getData(), action);
            entity.addAction(action);
        }

        for (FlowEventDump entry : view.getEventDumps()) {
            HibernateFlowEventDump dump = new HibernateFlowEventDump();
            FlowEventDumpCloner.INSTANCE.copy(entry.getData(), dump);
            entity.addDump(dump);
        }

        return entity;
    }

    @Override
    protected FlowEventData doDetach(FlowEvent model, HibernateFlowEvent entity) {
        return FlowEventCloner.INSTANCE.deepCopy(entity);
    }

    private List<HibernateFlowEvent> fetch(String flowId, Instant timeFrom, Instant timeTo, int maxCount) {
        CriteriaBuilder builder = getSession().getCriteriaBuilder();
        CriteriaQuery<HibernateFlowEvent> query = builder.createQuery(HibernateFlowEvent.class);
        Root<HibernateFlowEvent> root = query.from(HibernateFlowEvent.class);
        query.select(root);
        query.where(makeQueryFilter(root, flowId, timeFrom, timeTo).toArray(new Predicate[0]));
        query.orderBy(
                builder.desc(root.get(HibernateFlowEvent_.eventTime)),
                builder.desc(root.get(HibernateFlowEvent_.flowId)));
        return getSession().createQuery(query).setMaxResults(maxCount).getResultList();
    }

    private List<Predicate> makeQueryFilter(
            Root<HibernateFlowEvent> root, String flowId, Instant timeFrom, Instant timeTo) {
        List<Predicate> filters = new ArrayList<>(3);
        CriteriaBuilder builder = getSession().getCriteriaBuilder();
        filters.add(builder.equal(root.get(HibernateFlowEvent_.flowId), flowId));
        if (timeFrom != null) {
            filters.add(builder.greaterThanOrEqualTo(root.get(HibernateFlowEvent_.eventTime), timeFrom));
        }
        if (timeTo != null) {
            filters.add(builder.lessThan(root.get(HibernateFlowEvent_.eventTime), timeTo));
        }
        return filters;
    }

    private Optional<FlowStatusView> extractStatusUpdates(HibernateFlowEventAction actionEntry) {
        String action = actionEntry.getAction();
        if (action.equals(FlowEvent.FLOW_DELETED_ACTION)) {
            return Optional.of(new FlowStatusView(actionEntry.getTimestamp(), "DELETED"));
        }
        for (String actionPart : FlowEvent.FLOW_STATUS_ACTION_PARTS) {
            if (action.contains(actionPart)) {
                return Optional.of(new FlowStatusView(actionEntry.getTimestamp(),
                        action.replace(actionPart, "")));
            }
        }

        return Optional.empty();
    }
}
