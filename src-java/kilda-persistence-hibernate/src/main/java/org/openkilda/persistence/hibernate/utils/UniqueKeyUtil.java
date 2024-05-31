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

package org.openkilda.persistence.hibernate.utils;

import org.openkilda.persistence.exceptions.PersistenceException;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.apache.commons.codec.digest.DigestUtils;
import org.hibernate.Session;

import java.util.List;
import java.util.Optional;

/**
 * This utility is useful together with the indexed column 'taskIdUniqueKey' in DB.
 * <p>
 * When some column values
 * are not suitable for the indexed keys, use the method from this utility to make a unique key better for indexing
 * and then use the corresponding method to retrieve the entity using this indexed column.
 * </p>
 */
public final class UniqueKeyUtil {

    private UniqueKeyUtil() {
    }

    private static final String TASK_ID_UNIQUE_KEY_ATTRIBUTE = "taskIdUniqueKey";
    private static final String TASK_ID_ATTRIBUTE = "taskId";

    public static String makeTaskIdUniqueKey(String value) {
        return String.format("%s:%x:sha256", DigestUtils.sha256Hex(value), value.length());
    }

    /**
     * Common method for both simple and HA flows history. Retrieves an entity from DB using a special key optimised
     * for better indexing.
     *
     * @param taskId  taskId from entity (typically the field with the same name; usually contains correlation ID)
     * @param session a session from Hibernate
     * @param type    the type of the Entity that supports task ID unique key (for example HibernateHaFlowEvent)
     * @param <T>     entity type that supports task ID unique key.
     * @return the entity of type T from DB
     */
    public static <T> Optional<T> findEntityUsingTaskIdUniqueKey(String taskId, Session session, Class<T> type) {
        String taskIdKey = UniqueKeyUtil.makeTaskIdUniqueKey(taskId);
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(type);
        Root<T> root = query.from(type);
        query.select(root);
        query.where(builder.equal(root.get(TASK_ID_UNIQUE_KEY_ATTRIBUTE), taskIdKey));
        List<T> results = session.createQuery(query).getResultList();

        if (1 < results.size()) {
            throw new PersistenceException(String.format(
                    "Unique constraint violation on field %s of %s. %s is %s",
                    TASK_ID_ATTRIBUTE, type.getName(),
                    TASK_ID_UNIQUE_KEY_ATTRIBUTE, taskIdKey));
        }
        if (!results.isEmpty()) {
            return Optional.of(results.get(0));
        }
        return Optional.empty();
    }
}
