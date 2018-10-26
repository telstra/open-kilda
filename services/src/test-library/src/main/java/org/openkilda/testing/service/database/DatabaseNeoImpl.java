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

package org.openkilda.testing.service.database;

import static org.neo4j.driver.v1.Values.NULL;
import static org.openkilda.testing.Constants.DEFAULT_COST;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DatabaseNeoImpl implements DisposableBean, Database {
    private static final int DEFAULT_DEPTH = 7;
    private static final String MATCH_LINK_QUERY = "MATCH ()-[link:isl {src_port:$srcPort, dst_port:$dstPort, "
            + "src_switch:$srcSwitch, dst_switch:$dstSwitch}]->()";

    private Driver neo;

    public DatabaseNeoImpl(@org.springframework.beans.factory.annotation.Value("${neo.uri}") String neoUri) {
        neo = GraphDatabase.driver(neoUri);
    }

    @Override
    public void destroy() {
        if (neo != null) {
            try {
                neo.close();
            } catch (Exception e) {
                log.warn("Error on closing Neo4j connection", e);
            }
        }
    }

    /**
     * Updates certain property on a certain ISL.
     *
     * @return true if at least 1 ISL was affected
     */
    @Override
    public boolean updateLinkProperty(Isl isl, String property, Object value) {
        String query = MATCH_LINK_QUERY + " SET link += {props}";
        Map<String, Object> params = getParams(isl);
        params.put("props", ImmutableMap.of(property, value));
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, params);
        }
        return result.summary().counters().propertiesSet() > 0;
    }

    /**
     * Set ISL's max bandwidth to be equal to its speed (the default situation).
     *
     * @param isl ISL to be changed
     * @return true if at least 1 ISL was affected
     */
    @Override
    public boolean revertIslBandwidth(Isl isl) {
        String query = MATCH_LINK_QUERY + " SET link.max_bandwidth=link.speed, link.available_bandwidth=link.speed";
        Map<String, Object> params = getParams(isl);
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, params);
        }
        return result.summary().counters().propertiesSet() > 0;
    }

    @Override
    public boolean removeInactiveIsls() {
        String query = "MATCH ()-[i:isl]->() WHERE i.status<>'active' DELETE i";
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query);
        }
        return result.summary().counters().relationshipsDeleted() > 0;
    }

    @Override
    public boolean removeInactiveSwitches() {
        String query = "MATCH (s:switch) WHERE s.state<>'active' DETACH DELETE s";
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query);
        }
        return result.summary().counters().nodesDeleted() > 0;
    }

    @Override
    public boolean resetCosts() {
        String query = "MATCH ()-[i:isl]->() SET i.cost=$cost";
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, ImmutableMap.of("cost", DEFAULT_COST));
        }
        return result.summary().counters().propertiesSet() > 0;
    }

    /**
     * Get ISL cost.
     *
     * @param isl ISL for which cost should be retrieved
     * @return ISL cost
     */
    @Override
    public int getIslCost(Isl isl) {
        String query = MATCH_LINK_QUERY + " RETURN link.cost";
        Map<String, Object> params = getParams(isl);
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, params);
        }
        Value cost = result.single().get("link.cost");
        return cost != NULL ? cost.asInt() : DEFAULT_COST;
    }

    @Override
    public int countFlows() {
        String query = "MATCH ()-[f:flow]-() RETURN count(f)";
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query);
        }
        return result.single().get("count(f)").asInt();
    }

    @Override
    public List<PathInfoData> getPaths(SwitchId src, SwitchId dst) {
        String query = "match p=(:switch {name: {src_switch}})-[:isl*.." + DEFAULT_DEPTH + "]->"
                + "(:switch {name: {dst_switch}}) "
                + "WHERE ALL(x IN NODES(p) WHERE SINGLE(y IN NODES(p) WHERE y = x)) "
                + "WITH RELATIONSHIPS(p) as links "
                + "WHERE ALL(l IN links WHERE l.status = 'active') "
                + "return links";
        Map<String, Object> params = new HashMap<>(2);
        params.put("src_switch", src.toString());
        params.put("dst_switch", dst.toString());
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, params);
        }
        List<PathInfoData> deserializedResults = new ArrayList<>();
        for (Record record : result.list()) {
            List<PathNode> path = new ArrayList<>();
            int seqId = 0;
            for (Value link : record.get("links").values()) {
                path.add(new PathNode(new SwitchId(link.get("src_switch").asString()),
                        link.get("src_port").asInt(), seqId++, link.get("latency").asLong()));
                path.add(new PathNode(new SwitchId(link.get("dst_switch").asString()),
                        link.get("dst_port").asInt(), seqId++, link.get("latency").asLong()));
            }
            deserializedResults.add(new PathInfoData(0, path));
        }
        return deserializedResults;
    }

    @Override
    public FlowPair<Flow, Flow> getFlow(String flowId) {
        String query = "MATCH (a:switch)-[r:flow]->(b:switch) "
                + "WHERE r.flowid = {flow_id} "
                + "RETURN r";
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, ImmutableMap.of("flow_id", flowId));
        }
        List<Record> flows = result.list();
        if (flows.size() < 2) {
            return null;
        }
        return new FlowPair<>(convert(flows.get(0).get("r")), convert(flows.get(1).get("r")));
    }

    private Flow convert(Value flow) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> f = new HashMap<>(flow.asMap());
        Map<String, Object> p = null;
        try {
            p = mapper.readValue(f.get("flowpath").toString(), new TypeReference<HashMap<String, Object>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        p.put("clazz", "org.openkilda.messaging.info.event.PathInfoData");
        f.put("flowpath", p);
        f.put("periodic-pings", f.remove("periodic_pings"));
        return mapper.convertValue(f, Flow.class);
    }

    private Map<String, Object> getParams(Isl isl) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("srcPort", isl.getSrcPort());
        params.put("dstPort", isl.getDstPort());
        params.put("srcSwitch", isl.getSrcSwitch().getDpId().toString());
        params.put("dstSwitch", isl.getDstSwitch().getDpId().toString());
        return params;
    }
}
