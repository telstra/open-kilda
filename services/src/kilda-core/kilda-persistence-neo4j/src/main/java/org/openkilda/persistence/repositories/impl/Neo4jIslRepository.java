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

import static java.lang.String.format;
import static org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository.SWITCH_NAME_PROPERTY_NAME;

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.IslStatusConverter;
import org.openkilda.persistence.converters.SwitchStatusConverter;
import org.openkilda.persistence.repositories.IslRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link IslRepository}.
 */
public class Neo4jIslRepository extends Neo4jGenericRepository<Isl> implements IslRepository {
    private static final String SRC_PORT_PROPERTY = "src_port";
    private static final String DST_PORT_PROPERTY = "dst_port";

    private static final String REQUESTED_BANDWIDTH_PROPERTY = "requested_bandwidth";
    private static final String SWITCH_STATUS_PROPERTY = "switch_status";
    private static final String ISL_STATUS_PROPERTY = "isl_status";

    private final SwitchStatusConverter switchStatusConverter = new SwitchStatusConverter();
    private final IslStatusConverter islStatusConverter = new IslStatusConverter();

    public Neo4jIslRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<Isl> findBySrcEndpoint(SwitchId srcSwitchId, int srcPort) {
        Filter srcSwitchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS,
                srcSwitchId.toString());
        srcSwitchFilter.setNestedPath(new Filter.NestedPathSegment("srcSwitch", Switch.class));
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY, ComparisonOperator.EQUALS, srcPort);

        return getSession().loadAll(getEntityType(), srcSwitchFilter.and(srcPortFilter), DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<Isl> findByDestEndpoint(SwitchId dstSwitchId, int dstPort) {
        Filter dstSwitchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS,
                dstSwitchId.toString());
        dstSwitchFilter.setNestedPath(new Filter.NestedPathSegment("destSwitch", Switch.class));
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY, ComparisonOperator.EQUALS, dstPort);

        return getSession().loadAll(getEntityType(), dstSwitchFilter.and(dstPortFilter), DEPTH_LOAD_ENTITY);
    }

    @Override
    public Optional<Isl> findByEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        Filter srcSwitchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS,
                srcSwitchId.toString());
        srcSwitchFilter.setNestedPath(new Filter.NestedPathSegment("srcSwitch", Switch.class));
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY, ComparisonOperator.EQUALS, srcPort);
        Filter dstSwitchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS,
                dstSwitchId.toString());
        dstSwitchFilter.setNestedPath(new Filter.NestedPathSegment("destSwitch", Switch.class));
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY, ComparisonOperator.EQUALS, dstPort);

        Collection<Isl> isls = getSession().loadAll(getEntityType(),
                srcSwitchFilter.and(srcPortFilter).and(dstSwitchFilter).and(dstPortFilter), DEPTH_LOAD_ENTITY);
        if (isls.size() > 1) {
            throw new PersistenceException(format("Found more that 1 ISL entity with %s_%d - %s_%d",
                    srcSwitchId, srcPort, dstSwitchId, dstPort));
        }
        return isls.isEmpty() ? Optional.empty() : Optional.of(isls.iterator().next());
    }

    @Override
    public Collection<Isl> findActiveAndOccupiedByFlowWithAvailableBandwidth(String flowId, long requiredBandwidth) {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_id", flowId,
                REQUESTED_BANDWIDTH_PROPERTY, requiredBandwidth,
                SWITCH_STATUS_PROPERTY, switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                ISL_STATUS_PROPERTY, islStatusConverter.toGraphProperty(IslStatus.ACTIVE));

        String query = "MATCH (src:switch)-[fs:flow_segment{flowid: $flow_id}]->(dst:switch) "
                + "MATCH (src)-[link:isl]->(dst) "
                + "WHERE src.state = $switch_status AND dst.state = $switch_status AND link.status = $isl_status "
                + " AND link.src_port = fs.src_port AND link.dst_port = fs.dst_port "
                + " AND link.available_bandwidth + fs.bandwidth >= $requested_bandwidth "
                + "RETURN src, link, dst";

        return Lists.newArrayList(getSession().query(getEntityType(), query, parameters));
    }

    @Override
    public Collection<Isl> findAllActive() {
        // 0 bandwidth means ignore it.
        return findActiveWithAvailableBandwidth(0L);
    }

    @Override
    public Collection<Isl> findActiveWithAvailableBandwidth(long requiredBandwidth) {
        Map<String, Object> parameters = ImmutableMap.of(
                REQUESTED_BANDWIDTH_PROPERTY, requiredBandwidth,
                SWITCH_STATUS_PROPERTY, switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                ISL_STATUS_PROPERTY, islStatusConverter.toGraphProperty(IslStatus.ACTIVE));

        String query = "MATCH (src:switch)-[link:isl]->(dst:switch) "
                + "WHERE src.state = $switch_status AND dst.state = $switch_status AND link.status = $isl_status "
                + " AND link.available_bandwidth >= $requested_bandwidth "
                + "RETURN src, link, dst";

        return Lists.newArrayList(getSession().query(getEntityType(), query, parameters));
    }

    @Override
    public Collection<Isl> findSymmetricActiveWithAvailableBandwidth(long requiredBandwidth) {
        Map<String, Object> parameters = ImmutableMap.of(
                REQUESTED_BANDWIDTH_PROPERTY, requiredBandwidth,
                SWITCH_STATUS_PROPERTY, switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                ISL_STATUS_PROPERTY, islStatusConverter.toGraphProperty(IslStatus.ACTIVE));

        String query = "MATCH (source:switch)-[link:isl]->(dest:switch) "
                + "MATCH (dest)-[reverse:isl{src_port: link.dst_port, dst_port: link.src_port}]->(source) "
                + "WHERE source.state = $switch_status AND dest.state = $switch_status AND link.status = $isl_status "
                + " AND link.available_bandwidth >= $requested_bandwidth "
                + " AND reverse.available_bandwidth >= $requested_bandwidth "
                + "RETURN source, link, dest";

        return Lists.newArrayList(getSession().query(getEntityType(), query, parameters));
    }

    @Override
    public void createOrUpdate(Isl link) {
        transactionManager.doInTransaction(() -> {
            lockSwitches(requireManagedEntity(link.getSrcSwitch()), requireManagedEntity(link.getDestSwitch()));

            super.createOrUpdate(link);
        });
    }

    @Override
    Class<Isl> getEntityType() {
        return Isl.class;
    }
}
