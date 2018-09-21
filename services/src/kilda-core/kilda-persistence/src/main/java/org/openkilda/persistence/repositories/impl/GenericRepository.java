/* Copyright 2018 Telstra Open Source
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

package org.openkilda.persistence.repositories.impl;

import org.openkilda.persistence.neo4j.Neo4jSessionFactory;
import org.openkilda.persistence.repositories.Repository;

import org.neo4j.ogm.session.Session;

import java.util.Collection;

abstract class GenericRepository<T> implements Repository<T> {
    private static final int DEPTH_LIST = 1;
    private static final int DEPTH_ENTITY = 1;

    protected Session getSession() {
        return Neo4jSessionFactory.INSTANCE.getSession();
    }

    @Override
    public Collection<T> findAll() {
        return getSession().loadAll(getEntityType(), DEPTH_LIST);
    }

    @Override
    public void delete(T entity) {
        getSession().delete(entity);
    }

    @Override
    public void createOrUpdate(T entity) {
        getSession().save(entity, DEPTH_ENTITY);
    }

    abstract Class<T> getEntityType();
}
