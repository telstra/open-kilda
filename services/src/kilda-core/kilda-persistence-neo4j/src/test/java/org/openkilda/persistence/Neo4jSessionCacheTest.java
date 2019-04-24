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

package org.openkilda.persistence;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class Neo4jSessionCacheTest extends Neo4jBasedTest {
    static final String FLOW_ID_PROPERTY_NAME = "flow_id";
    static final String TEST_FLOW_ID = "test_flow";

    @Test
    public void shouldSessionSharesEntityInstances() {
        initFlow();

        Session session = ((Neo4jSessionFactory) persistenceManager.getTransactionManager()).getSession();
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, TEST_FLOW_ID);
        Flow fetchedFlow = session.loadAll(Flow.class, flowIdFilter).iterator().next();

        assertEquals(FlowStatus.IN_PROGRESS, fetchedFlow.getStatus());

        Flow anotherInstance = session.loadAll(Flow.class, flowIdFilter).iterator().next();
        anotherInstance.setStatus(FlowStatus.DOWN);
        session.save(anotherInstance);

        assertEquals(FlowStatus.DOWN, fetchedFlow.getStatus());
    }

    @Test
    public void shouldReloadEntityAfterDetaching() {
        initFlow();

        Session session = ((Neo4jSessionFactory) persistenceManager.getTransactionManager()).getSession();
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, TEST_FLOW_ID);
        Flow fetchedFlow = session.loadAll(Flow.class, flowIdFilter).iterator().next();

        assertEquals(FlowStatus.IN_PROGRESS, fetchedFlow.getStatus());

        String query = "MATCH ()-[:source]-(f:flow {flow_id: {flow_id}})-[:destination]-() "
                + "SET f.status='up' RETURN id(f)";
        Map<String, Object> parameters = ImmutableMap.of("flow_id", TEST_FLOW_ID);
        Iterable<Long> flowRelationIds = session.query(Long.class, query, parameters);
        // 'refresh' the Flow entity in OGM cache.
        flowRelationIds.forEach(session::detachNodeEntity);

        assertEquals(FlowStatus.IN_PROGRESS, fetchedFlow.getStatus());

        Collection<Flow> flows = session.loadAll(Flow.class, flowIdFilter);
        assertThat(flows, hasSize(1));
        Flow afterUpdateFlow = flows.iterator().next();

        assertEquals(FlowStatus.UP, afterUpdateFlow.getStatus());
    }

    @Test
    public void shouldSaveEntityAfterDetaching() {
        initFlow();

        Session session = ((Neo4jSessionFactory) persistenceManager.getTransactionManager()).getSession();
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, TEST_FLOW_ID);
        Flow fetchedFlow = session.loadAll(Flow.class, flowIdFilter).iterator().next();

        assertEquals(FlowStatus.IN_PROGRESS, fetchedFlow.getStatus());

        String query = "MATCH ()-[:source]-(f:flow {flow_id: {flow_id}})-[:destination]-() "
                + "SET f.status='up' RETURN id(f)";
        Map<String, Object> parameters = ImmutableMap.of("flow_id", TEST_FLOW_ID);
        Iterable<Long> flowRelationIds = session.query(Long.class, query, parameters);
        // 'refresh' the Flow entity in OGM cache.
        flowRelationIds.forEach(session::detachRelationshipEntity);

        assertEquals(FlowStatus.IN_PROGRESS, fetchedFlow.getStatus());

        fetchedFlow.setStatus(FlowStatus.DOWN);
        session.save(fetchedFlow);

        Collection<Flow> flows = session.loadAll(Flow.class, flowIdFilter);
        assertThat(flows, hasSize(1));
        Flow afterUpdateFlow = flows.iterator().next();

        assertEquals(FlowStatus.DOWN, afterUpdateFlow.getStatus());
    }

    private void initFlow() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();

        Switch switchA = buildTestSwitch(1);
        repositoryFactory.createSwitchRepository().createOrUpdate(switchA);

        Switch switchB = buildTestSwitch(2);
        repositoryFactory.createSwitchRepository().createOrUpdate(switchB);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.IN_PROGRESS)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(1))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID + "_reverse_path"))
                .flow(flow)
                .cookie(new Cookie(2))
                .meterId(new MeterId(2))
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reversePath);

        repositoryFactory.createFlowRepository().createOrUpdate(flow);
    }
}
