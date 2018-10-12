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

import org.openkilda.model.Flow;
import org.openkilda.persistence.repositories.FlowRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Neo4J OGM implementation of {@link FlowRepository}.
 */
public class Neo4jFlowRepository extends Neo4jGenericRepository<Flow> implements FlowRepository {
    public Neo4jFlowRepository(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    public Iterable<Flow> findById(String flowId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("flowid", flowId);

        return getSession().query(Flow.class, "MATCH (a)-[f:flow{flowid: {flowid}}]-(b) RETURN a,f,b", parameters);
    }

    @Override
    public void mergeFlowRelationships(Flow flow) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src_switch", flow.getSrcSwitchId());
        parameters.put("dst_switch", flow.getDestSwitch());
        parameters.put("flowId", flow.getFlowId());
        parameters.put("cookie", flow.getCookie());
        parameters.put("src_port", flow.getSrcPort());
        parameters.put("src_vlan", flow.getSrcVlan());
        parameters.put("dst_port", flow.getSrcPort());
        parameters.put("dst_vlan", flow.getDestVlan());
        parameters.put("meter_id", flow.getMeterId());
        parameters.put("bandwidth", flow.getBandwidth());
        parameters.put("ignore_bandwidth", flow.isIgnoreBandwidth());
        parameters.put("periodic_pings", flow.isPeriodicPings());
        parameters.put("transit_vlan", flow.getTransitVlan());
        parameters.put("description", flow.getDescription());
        parameters.put("last_updated", new Long(System.currentTimeMillis()).toString());
        parameters.put("flowpath", flow.getFlowPath());
        String query = "MERGE (src:switch {name: $src_switch}) "
                + " ON CREATE SET src.state = 'inactive' "
                + "MERGE (dst:switch {name: $dst_switch}) "
                + " ON CREATE SET dst.state = 'inactive' "
                + "MERGE (src)-[f:flow {"
                + " flowid: $flowid, "
                + " cookie: $cookie } ]->(dst)"
                + "SET f.src_switch = src.name, "
                + " f.src_port = $src_port, "
                + " f.src_vlan = $src_vlan, "
                + " f.dst_switch = dst.name, "
                + " f.dst_port = $dst_port, "
                + " f.dst_vlan = $dst_vlan, "
                + " f.meter_id = $meter_id, "
                + " f.bandwidth = $bandwidth, "
                + " f.ignore_bandwidth = $ignore_bandwidth, "
                + " f.periodic_pings = $periodic_pings, "
                + " f.transit_vlan = $transit_vlan, "
                + " f.description = $description, "
                + " f.last_updated = $last_updated, "
                + " f.flowpath = $flowpath";
        getSession().query(Flow.class, query, parameters);
    }

    @Override
    Class<Flow> getEntityType() {
        return Flow.class;
    }
}
