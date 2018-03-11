/* Copyright 2017 Telstra Open Source
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

package org.openkilda.pce.provider;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Statement;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NeoDriver implements PathComputer {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NeoDriver.class);

    private static final String PATH_QUERY_FORMATTER_PATTERN_Dijkstra =
            "MATCH (a:switch{name:{src_switch}}),(b:switch{name:{dst_switch}}), " +
                    "CALL apoc.algo.dijkstra(from, to, 'CONNECTED_TO', 'distance') YIELD path AS path, weight AS weight " +
                    "where ALL(x in nodes(p) WHERE x.state = 'active') " +
                    "AND ALL(y in r WHERE y.available_bandwidth >= {bandwidth} AND y.status = 'active') " +
                    "RETURN p";

    private static final String PATH_QUERY_FORMATTER_PATTERN_Dijkstra_NO_BW =
            "MATCH (a:switch{name:{src_switch}}),(b:switch{name:{dst_switch}}), " +
                    "p = shortestPath((a)-[r:isl*..100]->(b)) " +
                    "where ALL(x in nodes(p) WHERE x.state = 'active') " +
                    "AND ALL(y in r WHERE y.available_bandwidth >= {bandwidth} AND y.status = 'active') " +
                    "RETURN p";

    /**
     * {@link Driver} instance.
     */
    private final Driver driver;

    /**
     * @param driver NEO4j driver(connect)
     */
    public NeoDriver(Driver driver) {
        this.driver = driver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy)
            throws UnroutablePathException {
        /*
         * TODO: implement strategy
         */

        long latency = 0L;
        List<PathNode> forwardNodes = new LinkedList<>();
        List<PathNode> reverseNodes = new LinkedList<>();

        if (! flow.isOneSwitchFlow()) {
            Statement statement = makePathQuery(flow);
            logger.debug("QUERY: {}", statement.toString());

            try (Session session = driver.session()) {
                StatementResult result = session.run(statement);

                try {
                    Record record = result.next();

                    LinkedList<Relationship> isls = new LinkedList<>();
                    record.get(0).asPath().relationships().forEach(isls::add);

                    int seqId = 0;
                    for (Relationship isl : isls) {
                        latency += isl.get("latency").asLong();

                        forwardNodes.add(new PathNode(isl.get("src_switch").asString(),
                                isl.get("src_port").asInt(), seqId, isl.get("latency").asLong()));
                        seqId++;

                        forwardNodes.add(new PathNode(isl.get("dst_switch").asString(),
                                isl.get("dst_port").asInt(), seqId, 0L));
                        seqId++;
                    }

                    seqId = 0;
                    Collections.reverse(isls);

                    for (Relationship isl : isls) {
                        reverseNodes.add(new PathNode(isl.get("dst_switch").asString(),
                                isl.get("dst_port").asInt(), seqId, isl.get("latency").asLong()));
                        seqId++;

                        reverseNodes.add(new PathNode(isl.get("src_switch").asString(),
                                isl.get("src_port").asInt(), seqId, 0L));
                        seqId++;
                    }
                } catch (NoSuchRecordException e) {
                    throw new UnroutablePathException(flow);
                }
            }
        } else {
            logger.info("No path computation for one-switch flow");
        }

        return new ImmutablePair<>(new PathInfoData(latency, forwardNodes), new PathInfoData(latency, reverseNodes));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<FlowInfo> getFlowInfo(){
        List<FlowInfo> flows = new ArrayList<>();
        String subject = "MATCH (:switch)-[f:flow]->(:switch) " +
                "RETURN f.flowid as flow_id, " +
                " f.cookie as cookie, " +
                " f.meter_id as meter_id, " +
                " f.transit_vlan as transit_vlan, " +
                " f.src_switch as src_switch";

        Session session = driver.session();
        StatementResult result = session.run(subject);
        for (Record record : result.list()) {
            flows.add(new FlowInfo()
                    .setFlowId(record.get("flow_id").asString())
                    .setSrcSwitchId(record.get("src_switch").asString())
                    .setCookie(record.get("cookie").asLong())
                    .setMeterId(record.get("meter_id").asInt())
                    .setTransitVlanId(record.get("transit_vlan").asInt())
            );
        }
        return flows;
    }

    private Statement makePathQuery(Flow flow) {
        HashMap<String,Value> parameters = new HashMap<>();

        String subject =
                "MATCH (a:switch{name:{src_switch}}),(b:switch{name:{dst_switch}}), " +
                "p = shortestPath((a)-[r:isl*..100]->(b))";
        parameters.put("src_switch", Values.value(flow.getSourceSwitch()));
        parameters.put("dst_switch", Values.value(flow.getDestinationSwitch()));

        StringJoiner where = new StringJoiner("\n    AND ", "where ", "");
        where.add("ALL(x in nodes(p) WHERE x.state = 'active')");
        if (flow.isIgnoreBandwidth()) {
            where.add("ALL(y in r WHERE y.status = 'active')");
        } else {
            where.add("ALL(y in r WHERE y.status = 'active' AND y.available_bandwidth >= {bandwidth})");
            parameters.put("bandwidth", Values.value(flow.getBandwidth()));
        }

        String result = "RETURN p";

        String query = String.join("\n", subject, where.toString(), result);
        return new Statement(query, Values.value(parameters));
    }
}
