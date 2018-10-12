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

import org.openkilda.testing.model.topology.TopologyDefinition.Isl;

import com.google.common.collect.ImmutableMap;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DatabaseNeoImpl implements DisposableBean, Database {
    private static final String MATCH_LINK_QUERY = "MATCH ()-[link:isl {src_port:$srcPort, dst_port:$dstPort, "
            + "src_switch:$srcSwitch, dst_switch:$dstSwitch}]->()";

    private Driver neo;

    public DatabaseNeoImpl(@Value("${neo.uri}") String neoUri) {
        neo = GraphDatabase.driver(neoUri);
    }

    @Override
    public void destroy() {
        if (neo != null) {
            neo.close();
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
        org.neo4j.driver.v1.Value cost = result.single().get("link.cost");
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

    private Map<String, Object> getParams(Isl isl) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("srcPort", isl.getSrcPort());
        params.put("dstPort", isl.getDstPort());
        params.put("srcSwitch", isl.getSrcSwitch().getDpId().toString());
        params.put("dstSwitch", isl.getDstSwitch().getDpId().toString());
        return params;
    }
}
