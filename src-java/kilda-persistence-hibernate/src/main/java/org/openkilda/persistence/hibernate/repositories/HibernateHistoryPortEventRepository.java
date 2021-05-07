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

import org.openkilda.model.SwitchId;
import org.openkilda.model.history.PortEvent;
import org.openkilda.model.history.PortEvent.PortEventData;
import org.openkilda.persistence.hibernate.entities.history.HibernatePortEvent;
import org.openkilda.persistence.hibernate.entities.history.HibernatePortEvent_;
import org.openkilda.persistence.repositories.history.PortEventRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.hibernate.SessionFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

public class HibernateHistoryPortEventRepository
        extends HibernateGenericRepository<PortEvent, PortEventData, HibernatePortEvent>
        implements PortEventRepository {
    public HibernateHistoryPortEventRepository(
            TransactionManager transactionManager, Supplier<SessionFactory> factorySupplier) {
        super(transactionManager, factorySupplier);
    }

    @Override
    public List<PortEvent> findBySwitchIdAndPortNumber(
            SwitchId switchId, int portNumber, Instant start, Instant end) {
        return transactionManager.doInTransaction(
                () -> findEntityBySwitchIdAndPortNumber(switchId, portNumber, start, end).stream()
                        .map(PortEvent::new)
                        .collect(Collectors.toList()));
    }

    private List<HibernatePortEvent> findEntityBySwitchIdAndPortNumber(
            SwitchId switchId, int portNumber, Instant timeFrom, Instant timeTo) {
        CriteriaBuilder builder = getSession().getCriteriaBuilder();
        CriteriaQuery<HibernatePortEvent> query = builder.createQuery(HibernatePortEvent.class);
        Root<HibernatePortEvent> root = query.from(HibernatePortEvent.class);
        query.select(root);

        List<Predicate> filters = new ArrayList<>(3);
        filters.add(builder.equal(root.get(HibernatePortEvent_.switchId), switchId));
        filters.add(builder.equal(root.get(HibernatePortEvent_.portNumber), portNumber));
        if (timeFrom != null) {
            filters.add(builder.greaterThanOrEqualTo(root.get(HibernatePortEvent_.eventTime), timeFrom));
        }
        if (timeTo != null) {
            filters.add(builder.lessThan(root.get(HibernatePortEvent_.eventTime), timeTo));
        }
        query.where(filters.toArray(new Predicate[0]));

        query.orderBy(
                builder.asc(root.get(HibernatePortEvent_.eventTime)),
                builder.asc(root.get(HibernatePortEvent_.recordId)));

        return getSession().createQuery(query).getResultList();
    }

    @Override
    protected HibernatePortEvent makeEntity(PortEventData view) {
        HibernatePortEvent entity = new HibernatePortEvent();
        PortEvent.PortEventCloner.INSTANCE.copy(view, entity);
        return entity;
    }

    @Override
    protected PortEventData doDetach(PortEvent model, HibernatePortEvent entity) {
        return PortEvent.PortEventCloner.INSTANCE.deepCopy(entity);
    }
}
