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

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.Isl;
import org.openkilda.model.IslConfig;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowEncapsulationTypeConverter;
import org.openkilda.persistence.converters.IslStatusConverter;
import org.openkilda.persistence.converters.SwitchIdConverter;
import org.openkilda.persistence.converters.SwitchStatusConverter;
import org.openkilda.persistence.repositories.IslRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Neo4jSession;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Neo4j OGM implementation of {@link IslRepository}.
 */
public class Neo4jIslRepository extends Neo4jGenericRepository<Isl> implements IslRepository {
    private static final String SRC_PORT_PROPERTY_NAME = "src_port";
    private static final String DST_PORT_PROPERTY_NAME = "dst_port";

    private final SwitchIdConverter switchIdConverter = new SwitchIdConverter();
    private final SwitchStatusConverter switchStatusConverter = new SwitchStatusConverter();
    private final IslStatusConverter islStatusConverter = new IslStatusConverter();
    private final FlowEncapsulationTypeConverter flowEncapsulationTypeConverter = new FlowEncapsulationTypeConverter();

    private final IslConfig islConfig;

    public Neo4jIslRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager,
                              IslConfig islConfig) {
        super(sessionFactory, transactionManager);
        this.islConfig = islConfig;
    }

    @Override
    public Collection<Isl> findByEndpoint(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchId.toString(),
                "port", port);

        String query = "MATCH (s:switch)-[i:isl]->(d:switch) "
                + "WHERE (s.name = $switch_id AND i.src_port = $port) "
                + " OR (d.name = $switch_id AND i.dst_port = $port) "
                + "RETURN s, i, d";

        return addIslConfigToIsl(Lists.newArrayList(getSession().query(getEntityType(), query, parameters)));
    }

    @Override
    public Collection<Isl> findBySrcEndpoint(SwitchId srcSwitchId, int srcPort) {
        Filter srcSwitchFilter = createSrcSwitchFilter(srcSwitchId);
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, srcPort);

        return addIslConfigToIsl(loadAll(srcSwitchFilter.and(srcPortFilter)));
    }

    @Override
    public Collection<Isl> findByDestEndpoint(SwitchId dstSwitchId, int dstPort) {
        Filter dstSwitchFilter = createDstSwitchFilter(dstSwitchId);
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, dstPort);

        return addIslConfigToIsl(loadAll(dstSwitchFilter.and(dstPortFilter)));
    }

    @Override
    public Collection<Isl> findBySrcSwitch(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        return addIslConfigToIsl(loadAll(srcSwitchFilter));
    }

    @Override
    public Collection<Isl> findByDestSwitch(SwitchId switchId) {
        Filter destSwitchFilter = createDstSwitchFilter(switchId);
        return addIslConfigToIsl(loadAll(destSwitchFilter));
    }

    @Override
    public Optional<Isl> findByEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        Filter srcSwitchFilter = createSrcSwitchFilter(srcSwitchId);
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, srcPort);
        Filter dstSwitchFilter = createDstSwitchFilter(dstSwitchId);
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, dstPort);

        Collection<Isl> isls = loadAll(srcSwitchFilter.and(srcPortFilter).and(dstSwitchFilter).and(dstPortFilter));
        if (isls.size() > 1) {
            throw new PersistenceException(format("Found more that 1 ISL entity with %s_%d - %s_%d",
                    srcSwitchId, srcPort, dstSwitchId, dstPort));
        }

        if (isls.isEmpty()) {
            return Optional.empty();
        }

        Isl isl = isls.iterator().next();
        isl.setIslConfig(islConfig);
        return Optional.of(isl);
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

        return addIslConfigToIsl(loadAll(filters));
    }

    @Override
    public Collection<Isl> findActiveAndOccupiedByFlowPathWithAvailableBandwidth(
            List<PathId> pathIds, long requiredBandwidth, FlowEncapsulationType flowEncapsulationType) {
        Map<String, Object> parameters = ImmutableMap.of(
                "path_ids", pathIds.stream().map(PathId::getId).collect(Collectors.toList()),
                "requested_bandwidth", requiredBandwidth,
                "switch_status", switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                "isl_status", islStatusConverter.toGraphProperty(IslStatus.ACTIVE),
                "supported_transit_encapsulation",
                flowEncapsulationTypeConverter.toGraphProperty(flowEncapsulationType));

        String query = "MATCH (fp:flow_path)-[:owns]-(ps:path_segment) "
                + "WHERE fp.path_id IN $path_ids "
                + "MATCH (src_features:switch_features)<-[:has]-(src:switch)-[:source]-(ps)-[:destination]-"
                + "(dst:switch)-[:has]->(dst_features:switch_features) "
                + "MATCH (src)-[link:isl]->(dst) "
                + "WHERE src.state = $switch_status AND dst.state = $switch_status AND link.status = $isl_status "
                + " AND link.src_port = ps.src_port AND link.dst_port = ps.dst_port "
                + " AND link.available_bandwidth + fp.bandwidth >= $requested_bandwidth "
                + " AND $supported_transit_encapsulation IN src_features.supported_transit_encapsulation "
                + " AND $supported_transit_encapsulation IN dst_features.supported_transit_encapsulation "
                + "RETURN src, link, dst";

        return addIslConfigToIsl(Lists.newArrayList(getSession().query(getEntityType(), query, parameters)));
    }

    @Override
    public Collection<Isl> findAllActive() {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_status", switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                "isl_status", islStatusConverter.toGraphProperty(IslStatus.ACTIVE));

        String query = "MATCH (src:switch)-[link:isl]->(dst:switch) "
                + "WHERE src.state = $switch_status AND dst.state = $switch_status AND link.status = $isl_status "
                + "RETURN src, link, dst";

        return addIslConfigToIsl(Lists.newArrayList(getSession().query(getEntityType(), query, parameters)));
    }

    @Override
    public Collection<Isl> findActiveWithAvailableBandwidth(long requiredBandwidth,
                                                            FlowEncapsulationType flowEncapsulationType) {
        Map<String, Object> parameters = ImmutableMap.of(
                "requested_bandwidth", requiredBandwidth,
                "switch_status", switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                "isl_status", islStatusConverter.toGraphProperty(IslStatus.ACTIVE),
                "supported_transit_encapsulation",
                flowEncapsulationTypeConverter.toGraphProperty(flowEncapsulationType));

        String query = "MATCH (src_features:switch_features)<-[:has]-(src:switch)-[link:isl]->"
                + "(dst:switch)-[:has]->(dst_features:switch_features) "
                + "WHERE src.state = $switch_status AND dst.state = $switch_status AND link.status = $isl_status "
                + "AND link.available_bandwidth >= $requested_bandwidth "
                + "AND $supported_transit_encapsulation IN src_features.supported_transit_encapsulation "
                + "AND $supported_transit_encapsulation IN dst_features.supported_transit_encapsulation "
                + "RETURN src_features, src, link, dst, dst_features";

        return addIslConfigToIsl(Lists.newArrayList(getSession().query(getEntityType(), query, parameters)));
    }

    @Override
    public Collection<Isl> findSymmetricActiveWithAvailableBandwidth(long requiredBandwidth,
                                                                     FlowEncapsulationType flowEncapsulationType) {

        Map<String, Object> parameters = ImmutableMap.of(
                "required_bandwidth", requiredBandwidth,
                "active_switch", switchStatusConverter.toGraphProperty(SwitchStatus.ACTIVE),
                "active_isl", islStatusConverter.toGraphProperty(IslStatus.ACTIVE),
                "supported_transit_encapsulation",
                flowEncapsulationTypeConverter.toGraphProperty(flowEncapsulationType));

        String query = "MATCH  (src_features:switch_features)<-[:has]-(source:switch)-[link:isl]->"
                + "(dest:switch)-[:has]->(dst_features:switch_features) "
                + "MATCH (dest)-[reverse:isl {src_port: link.dst_port, dst_port: link.src_port}]->(source) "
                + "WHERE source.state = $active_switch AND dest.state = $active_switch AND link.status = $active_isl "
                + "AND link.available_bandwidth >= $required_bandwidth "
                + "AND reverse.available_bandwidth >= $required_bandwidth "
                + "AND $supported_transit_encapsulation IN src_features.supported_transit_encapsulation "
                + "AND $supported_transit_encapsulation IN dst_features.supported_transit_encapsulation "
                + "RETURN src_features, source, link, dest, dst_features";

        return addIslConfigToIsl(Lists.newArrayList(getSession().query(getEntityType(), query, parameters)));
    }

    private Collection<Isl> addIslConfigToIsl(Collection<Isl> isls) {
        isls.forEach(isl -> isl.setIslConfig(islConfig));
        return isls;
    }

    @Override
    public void createOrUpdate(Isl link) {
        requireManagedEntity(link.getSrcSwitch());
        requireManagedEntity(link.getDestSwitch());

        transactionManager.doInTransaction(() -> {
            lockSwitches(link.getSrcSwitch().getSwitchId(), link.getDestSwitch().getSwitchId());

            super.createOrUpdate(link);
        });
    }

    @Override
    public long updateAvailableBandwidth(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort,
                                         long usedBandwidth) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", switchIdConverter.toGraphProperty(srcSwitchId),
                "src_port", srcPort,
                "dst_switch", switchIdConverter.toGraphProperty(dstSwitchId),
                "dst_port", dstPort,
                "used_bandwidth", usedBandwidth);

        String query = "MATCH (src:switch {name: $src_switch}), (dst:switch {name: $dst_switch}) "
                + "MATCH (src)-[link:isl {src_port: $src_port, dst_port: $dst_port}]->(dst) "
                + "SET link.available_bandwidth = (link.max_bandwidth - $used_bandwidth) "
                + "RETURN id(link) as id, link.available_bandwidth as available_bandwidth";

        Result result = getSession().query(query, parameters);
        Iterator<Map<String, Object>> it = result.queryResults().iterator();
        if (!it.hasNext()) {
            throw new PersistenceException(format("ISL %s_%d - %s_%d not found to be updated",
                    srcSwitchId, srcPort, dstSwitchId, dstPort));
        }

        Map<String, Object> queryResult = it.next();
        Long updatedEntityId = (Long) queryResult.get("id");
        long updatedAvailableBandwidth = (Long) queryResult.get("available_bandwidth");

        Object updatedEntity = ((Neo4jSession) getSession()).context().getRelationshipEntity(updatedEntityId);
        if (updatedEntity instanceof Isl) {
            ((Isl) updatedEntity).setAvailableBandwidth(updatedAvailableBandwidth);
        } else if (updatedEntity != null) {
            throw new PersistenceException(format("Expected an ISL entity, but found %s.", updatedEntity));
        }

        return updatedAvailableBandwidth;
    }

    @Override
    protected Class<Isl> getEntityType() {
        return Isl.class;
    }
}
