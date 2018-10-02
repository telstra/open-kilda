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
import org.openkilda.model.Isl;
import org.openkilda.model.Node;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.IslRepository;

import org.neo4j.ogm.cypher.query.SortOrder;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4J OGM implementation of {@link IslRepository}.
 */
@SuppressWarnings("squid:S1192")
public class Neo4jIslRepository extends Neo4jGenericRepository<Isl> implements IslRepository {
    public Neo4jIslRepository(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    public Isl findByEndpoints(SwitchId sourceSwitchId, int sourcePort,
                               SwitchId destinationSwitchId, int destinationPort) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src_switch", sourceSwitchId.toString());
        parameters.put("src_port", sourcePort);
        parameters.put("dst_switch", destinationSwitchId.toString());
        parameters.put("dst_port", destinationPort);

        String query = "MATCH"
                + "(src:switch {name: $src_switch})"
                + "-"
                + "[target:isl { "
                + "src_switch: $src_switch, "
                + "src_port: $src_port, "
                + "dst_switch: $dst_switch, "
                + "dst_port: $dst_port "
                + "}]"
                + "->"
                + "(dst:switch {name: $dst_switch}) "
                + "RETURN src, target, dst";

        return getSession().queryForObject(Isl.class, query, parameters);
    }

    @Override
    public Iterable<Isl> findByEndpoint(SwitchId switchId, int port) {
        String query = "MATCH (src:switch)-[target:isl]-(dst:switch) "
                + "WHERE target.src_switch=$src_switch AND target.src_port=$src_port "
                + "RETURN src, target, dst";

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src_switch", switchId.toString());
        parameters.put("src_port", port);

        return getSession().query(Isl.class, query, parameters);
    }

    @Override
    public void setLinkPropsToIsl(Isl isl) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src_switch", isl.getSrcSwitchId().toString());
        parameters.put("src_port", isl.getSrcPort());
        parameters.put("dst_switch", isl.getDestSwitchId().toString());
        parameters.put("dst_port", isl.getDestPort());

        String query = "MATCH (src:switch)-[i:isl]->(dst:switch) "
                + "WHERE i.src_switch = $src_switch "
                + "AND i.src_port = $src_port "
                + "AND i.dst_switch = $dst_switch "
                + "AND i.dst_port = $dst_port "
                + "MATCH (lp:link_props) "
                + "WHERE lp.src_switch = $src_switch "
                + "AND lp.src_port = $src_port "
                + "AND lp.dst_switch = $dst_switch "
                + "AND lp.dst_port = $dst_port "
                + "SET i += lp ";

        getSession().query(Isl.class, query, parameters);
    }

    @Override
    public Iterable<Isl> findOccupiedByFlow(String flowId, boolean ignoreBandwidth, long requiredBandwidth) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("flow_id", flowId);
        parameters.put("ignore_bandwidth", ignoreBandwidth);
        parameters.put("requested_bandwidth", requiredBandwidth);

        String query = "MATCH (src:switch)-[fs:flow_segment{flowid: $flow_id}]->(dst:switch) "
                + "MATCH (src)-[link:isl]->(dst) "
                + "WHERE src.state = 'active' AND dst.state = 'active' AND link.status = 'active' "
                + " AND link.src_port = fs.src_port AND link.dst_port = fs.dst_port "
                + " AND ($ignore_bandwidth OR link.available_bandwidth + fs.bandwidth >= $requested_bandwidth) "
                + "RETURN src, link, dst";

        return getSession().query(Isl.class, query, parameters);
    }

    @Override
    public Iterable<Isl> findActiveWithAvailableBandwidth(boolean ignoreBandwidth, long requiredBandwidth) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("ignore_bandwidth", ignoreBandwidth);
        parameters.put("requested_bandwidth", requiredBandwidth);

        String query = "MATCH (src:switch)-[link:isl]->(dst:switch) "
                + "WHERE src.state = 'active' AND dst.state = 'active' AND link.status = 'active' "
                + " AND src.name IS NOT NULL AND dst.name IS NOT NULL "
                + " AND ($ignore_bandwidth OR link.available_bandwidth >= $requested_bandwidth) "
                + " RETURN src, link, dst";

        return getSession().query(Isl.class, query, parameters);
    }


    public Collection<Isl> findAllOrderedBySrcSwitch() {
        return getSession().loadAll(getEntityType(), new SortOrder("src_switch"));
    }


    /**
     * Update bandwidth for isl related to flow.
     * @param flow flow's Isls to be updated
     */
    @Override
    public void updateIslBandwidth(Flow flow) {
        List<Node> nodes = flow.getFlowPath().getNodes();
        for (int i = 0; i < nodes.size(); i += 2) {
            Node src = nodes.get(i);
            Node dst = nodes.get(i + 1);
            updateIslBandwidth(src.getSwitchId(), src.getPortNo(), dst.getSwitchId(), dst.getPortNo());
        }
    }

    @Override
    public void updateIslBandwidth(SwitchId sourceSwitchId, int sourcePort,
                                   SwitchId destinationSwitchId, int destinationPort) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src_switch", sourceSwitchId.toString());
        parameters.put("src_port", sourcePort);
        parameters.put("dst_switch", destinationSwitchId.toString());
        parameters.put("dst_port", destinationPort);

        String query = "MATCH "
                + " (src:switch {name: $src_switch}), "
                + " (dst:switch {name: $dst_switch}) "
                + "WITH src,dst "
                + "MATCH (src) - [i:isl { "
                + " src_port: $src_port, "
                + " dst_port: $dst_port "
                + "}] -> (dst) "
                + "WITH src,dst,i "
                + "OPTIONAL MATCH (src) - [fs:flow_segment { "
                + " src_port: $src_port, "
                + " dst_port: $dst_port, "
                + " ignore_bandwidth: false "
                + "}] -> (dst) "
                + "WITH sum(fs.bandwidth) AS used_bandwidth, i as i "
                + "SET i.available_bandwidth = i.max_bandwidth - used_bandwidth ";

        getSession().query(Isl.class, query, parameters);
    }

    @Override
    public void updateStatus(Isl isl) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status_up", "active");
        parameters.put("status_moved", "moved");
        parameters.put("status_down", "inactive");
        parameters.put("src_switch", isl.getSrcSwitchId().toString());
        parameters.put("src_port", isl.getSrcPort());
        parameters.put("dst_switch", isl.getDestSwitchId().toString());
        parameters.put("dst_port", isl.getDestPort());
        parameters.put("peer_src_switch", isl.getDestSwitchId().toString());
        parameters.put("peer_src_port", isl.getDestPort());
        parameters.put("peer_dst_switch", isl.getSrcSwitchId().toString());
        parameters.put("peer_dst_port", isl.getSrcPort());
        parameters.put("mtime", Instant.now(Clock.systemUTC()).toString());

        String query = "MATCH "
                + "  (:switch {name: $src_switch}) "
                + "  - "
                + "  [self:isl { "
                + "    src_switch: $src_switch, "
                + "    src_port: $src_port, "
                + "    dst_switch: $dst_switch, "
                + "    dst_port: $dst_port "
                + "  }] "
                + "  -> "
                + "  (:switch {name: $dst_switch}) "
                + "MATCH "
                + "  (:switch {name: $peer_src_switch}) "
                + "  - "
                + "  [peer:isl { "
                + "    src_switch: $peer_src_switch, "
                + "    src_port: $peer_src_port, "
                + "    dst_switch: $peer_dst_switch, "
                + "    dst_port: $peer_dst_port "
                + "  }] "
                + "  -> "
                + "  (:switch {name: $peer_dst_switch}) "
                + " "
                + "WITH self, peer, CASE "
                + "  WHEN self.actual = $status_up AND peer.actual = $status_up "
                + "    THEN $status_up "
                + "  WHEN self.actual = $status_moved OR peer.actual = $status_moved "
                + "    THEN $status_moved "
                + "  ELSE $status_down "
                + "END AS isl_status  "
                + " "
                + "SET self.status=isl_status "
                + "SET peer.status=isl_status "
                + "SET self.time_modify=$mtime, peer.time_modify=$mtime ";

        getSession().query(Isl.class, query, parameters);
    }

    @Override
    Class<Isl> getEntityType() {
        return Isl.class;
    }
}
