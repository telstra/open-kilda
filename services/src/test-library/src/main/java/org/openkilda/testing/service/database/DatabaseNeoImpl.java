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
    private Driver neo;

    public DatabaseNeoImpl(@Value("${neo.uri}") String neoUri) {
        neo = GraphDatabase.driver(neoUri);
    }

    @Override
    public void destroy() throws Exception {
        if (neo != null) {
            neo.close();
        }
    }

    @Override
    public boolean updateLinkProperty(Isl isl, String property, Object value) {
        String query = "MATCH ()-[link:isl {src_port:$srcPort, dst_port:$dstPort, src_switch:$srcSwitch, "
                + "dst_switch:$dstSwitch}]->() SET link += {props}";
        Map<String, Object> params = getParams(isl);
        params.put("props", ImmutableMap.of(property, value));
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, params);
        }
        return result.summary().counters().propertiesSet() > 0;
    }

    private Map<String, Object> getParams(Isl isl) {
        Map<String, Object> params = new HashMap<>();
        params.put("srcPort", isl.getSrcPort());
        params.put("dstPort", isl.getDstPort());
        params.put("srcSwitch", isl.getSrcSwitch().getDpId());
        params.put("dstSwitch", isl.getDstSwitch().getDpId());
        return params;
    }
}
