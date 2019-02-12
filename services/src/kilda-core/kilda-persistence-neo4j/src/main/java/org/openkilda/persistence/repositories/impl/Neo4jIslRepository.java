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

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
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
import org.neo4j.ogm.cypher.Filters;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link IslRepository}.
 */
public class Neo4jIslRepository extends Neo4jGenericRepository<Isl> implements IslRepository {
    private static final String SRC_PORT_PROPERTY_NAME = "src_port";
    private static final String DST_PORT_PROPERTY_NAME = "dst_port";

    private final SwitchStatusConverter switchStatusConverter = new SwitchStatusConverter();
    private final IslStatusConverter islStatusConverter = new IslStatusConverter();

    public Neo4jIslRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<Isl> findBySrcEndpoint(SwitchId srcSwitchId, int srcPort) {
        Filter srcSwitchFilter = createSrcSwitchFilter(srcSwitchId);
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, srcPort);

        return getSession().loadAll(getEntityType(), srcSwitchFilter.and(srcPortFilter), DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<Isl> findByDestEndpoint(SwitchId dstSwitchId, int dstPort) {
        Filter dstSwitchFilter = createDstSwitchFilter(dstSwitchId);
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, dstPort);

        return getSession().loadAll(getEntityType(), dstSwitchFilter.and(dstPortFilter), DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<Isl> findBySrcSwitch(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        return getSession().loadAll(getEntityType(), srcSwitchFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<Isl> findByDestSwitch(SwitchId switchId) {
        Filter destSwitchFilter = createDstSwitchFilter(switchId);
        return getSession().loadAll(getEntityType(), destSwitchFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public Optional<Isl> findByEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        Filter srcSwitchFilter = createSrcSwitchFilter(srcSwitchId);
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, srcPort);
        Filter dstSwitchFilter = createDstSwitchFilter(dstSwitchId);
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, dstPort);

        Collection<Isl> isls = getSession().loadAll(getEntityType(),
                srcSwitchFilter.and(srcPortFilter).and(dstSwitchFilter).and(dstPortFilter), DEPTH_LOAD_ENTITY);
        if (isls.size() > 1) {
            throw new PersistenceException(format("Found more that 1 ISL entity with %s_%d - %s_%d",
                    srcSwitchId, srcPort, dstSwitchId, dstPort));
        }
        return isls.isEmpty() ? Optional.empty() : Optional.of(isls.iterator().next());
    }

    @Override
    public Collection<Isl> findByPartialEndpoints(SwitchId srcSwitchId, Integer srcPort,
                                                  SwitchId dstSwitchId, Integer dstPort) {
        Filters filters = new Filters();
        if (srcSwitchId != null) {
            filters.and(createSrcSwitchFilter(srcSwitchId));
        }
        if (srcPort != null) {
            filters.and(new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, srcPort));
        }
        if (dstSwitchId != null) {
            filters.and(createDstSwitchFilter(dstSwitchId));
        }
        if (dstPort != null) {
            filters.and(new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, dstPort));
        }

        return getSession().loadAll(getEntityType(), filters, DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<Isl> findActiveAndOccupiedByFlowWithAvailableBandwidth(String flowId, long requiredBandwidth) {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_id", flowId,
                "requested_bandwidth", requiredBandwidth,
                "switch_status", switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                "isl_status", islStatusConverter.toGraphProperty(IslStatus.ACTIVE));

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
                "requested_bandwidth", requiredBandwidth,
                "switch_status", switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                "isl_status", islStatusConverter.toGraphProperty(IslStatus.ACTIVE));

        String query = "MATCH (src:switch)-[link:isl]->(dst:switch) "
                + "WHERE src.state = $switch_status AND dst.state = $switch_status AND link.status = $isl_status "
                + " AND link.available_bandwidth >= $requested_bandwidth "
                + "RETURN src, link, dst";

        return Lists.newArrayList(getSession().query(getEntityType(), query, parameters));
    }

    @Override
    public Collection<Isl> findSymmetricActiveWithAvailableBandwidth(long requiredBandwidth) {
        Map<String, Object> parameters = ImmutableMap.of(
                "required_bandwidth", requiredBandwidth,
                "active_switch", switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                "active_isl", islStatusConverter.toGraphProperty(IslStatus.ACTIVE));

        String query = "MATCH (source:switch)-[link:isl]->(dest:switch) "
                + "MATCH (dest)-[reverse:isl{src_port: link.dst_port, dst_port: link.src_port}]->(source) "
                + "WHERE source.state = $active_switch AND dest.state = $active_switch AND link.status = $active_isl "
                + " AND link.available_bandwidth >= $required_bandwidth "
                + " AND reverse.available_bandwidth >= $required_bandwidth "
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
