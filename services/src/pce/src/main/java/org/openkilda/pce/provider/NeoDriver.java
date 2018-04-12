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

import org.openkilda.messaging.info.event.*;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.api.FlowAdapter;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.model.EndPoint;
import org.openkilda.pce.model.SimpleIsl;
import org.openkilda.pce.model.SimpleSwitch;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Statement;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.exceptions.TransientException;
import org.neo4j.driver.v1.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NeoDriver implements PathComputer {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NeoDriver.class);

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
            throws UnroutablePathException, RecoverableException {

        long latency = 0L;
        List<PathNode> forwardNodes = new LinkedList<>();
        List<PathNode> reverseNodes = new LinkedList<>();

        if (! flow.isOneSwitchFlow()) {
            Statement statement = getPathQuery(flow, strategy);
            logger.info("QUERY: {}", statement.toString());

            try (Session session = driver.session()) {
                StatementResult result = session.run(statement);

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

            // FIXME(surabujin): Need to catch and trace exact exception thrown in recoverable places.
            } catch (TransientException e) {
                throw new RecoverableException("TransientError from neo4j", e);
            } catch (ClientException e) {
                throw new RecoverableException("ClientException from neo4j", e);

            } catch (NoSuchRecordException e) {
                throw new UnroutablePathException(flow);
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

    /**
     * @return the first one found, if it exists.
     */
    @Override
    public List<Flow> getFlow(String flowId) {
        List<Flow> found = getFlows(flowId);
        return found.size() > 0 ? found : null;
    }

    @Override
    public List<Flow> getFlows(String flowId) {
        String where = "WHERE f.flowid='" + flowId + "' ";
        return _getFlows(where);
    }

    @Override
    public List<Flow> getAllFlows() {
        String noWhere = " ";
        return _getFlows(noWhere);
    }


    private List<Flow> _getFlows(String whereClause) {
        String q =
                "MATCH (:switch)-[f:flow]->(:switch) " +
                        whereClause +
                        "RETURN f.flowid as flowid, " +
                        "f.bandwidth as bandwidth, " +
                        "f.ignore_bandwidth as ignore_bandwidth, " +
                        "f.cookie as cookie, " +
                        "f.description as description, " +
                        "f.last_updated as last_updated, " +
                        "f.src_switch as src_switch, " +
                        "f.dst_switch as dst_switch, " +
                        "f.src_port as src_port, " +
                        "f.dst_port as dst_port, " +
                        "f.src_vlan as src_vlan, " +
                        "f.dst_vlan as dst_vlan, " +
                        "f.flowpath as path, " +
                        "f.meter_id as meter_id, " +
                        "f.transit_vlan as transit_vlan";

        logger.debug("Executing getFlows Query: {}", q);
        Session session = driver.session();
        StatementResult queryResults = session.run(q);
        List<Flow> results = new LinkedList<>();
        for (Record record : queryResults.list()) {
            FlowAdapter adapter = new FlowAdapter(record);
            results.add(adapter.getFlow());
        }

        return results;
    }

    @Override
    public  List<SwitchInfoData> getSwitches() {
        String q =
                "MATCH (sw:switch) " +
                        "RETURN " +
                        "sw.name as name, " +
                        "sw.address as address, " +
                        "sw.hostname as hostname, " +
                        "sw.description as description, " +
                        "sw.controller as controller, " +
                        "sw.state as state " +
                        "order by sw.name";

        logger.debug("Executing getSwitches Query: {}", q);
        Session session = driver.session();
        StatementResult queryResults = session.run(q);
        List<SwitchInfoData> results = new LinkedList<>();
        for (Record record : queryResults.list()) {
            SwitchInfoData sw = new SwitchInfoData();
            sw.setAddress(record.get("address").asString());
            sw.setController(record.get("controller").asString());
            sw.setDescription(record.get("description").asString());
            sw.setHostname(record.get("hostname").asString());

            String status = record.get("state").asString();
            SwitchState st = ("active".equals(status)) ? SwitchState.ACTIVATED : SwitchState.CACHED;
            sw.setState(st);

            sw.setSwitchId(record.get("name").asString());
            results.add(sw);
        }
        return results;
    }

    /**
     * This will return a network where everything is active and the isls have the bandwidth needed.
     * This is a specialization of the general query to get the entire network.
     *
     * NB: If the more common case is required at some point, refactor this into reusing the getAll,
     * then filter the results.
     *
     * @param ignore_bandwidth if false, then filter the ISLs based on available_bandwidth
     * @param available_bandwidth
     * @return
     */
    @Override
    public AvailableNetwork getAvailableNetwork(boolean ignore_bandwidth, int available_bandwidth) {

        String q = "MATCH (src:switch)-[isl:isl]->(dst:switch)" +
                " WHERE src.state = 'active' AND dst.state = 'active' AND isl.status = 'active' " +
                "   AND src.name IS NOT NULL AND dst.name IS NOT NULL";
        if (!ignore_bandwidth)
                q += "   AND isl.available_bandwidth >= " + available_bandwidth;
        q += " RETURN src.name as src_name, dst.name as dst_name " +
                ", isl.src_port as src_port " +
                ", isl.dst_port as dst_port " +
                ", isl.cost as cost " +
                ", isl.latency as latency " +
                " ORDER BY src.name";

        logger.debug("Executing getAvailableNetwork Query: {}", q);
        AvailableNetwork network = new AvailableNetwork();
        Session session = driver.session();
        StatementResult queryResults = session.run(q);
        for (Record record : queryResults.list()) {
            network.initOneEntry(
                    record.get("src_name").asString(),
                    record.get("dst_name").asString(),
                    record.get("src_port").asInt(),
                    record.get("dst_port").asInt(),
                    record.get("cost").asInt(),
                    record.get("latency").asInt()
                    );
        }
        return network;
    }


    @Override
    public List<IslInfoData> getIsls() {

        String q =
                "MATCH (:switch)-[isl:isl]->(:switch) " +
                        "RETURN " +
                        "isl.src_switch as src_switch, " +
                        "isl.src_port as src_port, " +
                        "isl.dst_switch as dst_switch, " +
                        "isl.dst_port as dst_port, " +
                        "isl.speed as speed, " +
                        "isl.max_bandwidth as max_bandwidth, " +
                        "isl.latency as latency, " +
                        "isl.available_bandwidth as available_bandwidth, " +
                        "isl.status as status " +
                        "order by isl.src_switch";

        logger.debug("Executing getSwitches Query: {}", q);
        Session session = driver.session();
        StatementResult queryResults = session.run(q);
        List<IslInfoData> results = new LinkedList<>();
        for (Record record : queryResults.list()) {
            // max_bandwidth not used in IslInfoData
            List<PathNode> pathNodes = new ArrayList<>();
            PathNode src = new PathNode();
            src.setSwitchId(record.get("src_switch").asString());
            src.setPortNo(record.get("src_port").asInt());
            src.setSegLatency(record.get("latency").asInt());
            pathNodes.add(src);
            PathNode dst = new PathNode();
            dst.setSwitchId(record.get("dst_switch").asString());
            dst.setPortNo(record.get("dst_port").asInt());
            dst.setSegLatency(record.get("latency").asInt());
            pathNodes.add(dst);

            String status = record.get("status").asString();
            IslChangeType state = ("active".equals(status)) ? IslChangeType.DISCOVERED : IslChangeType.FAILED;

            IslInfoData isl = new IslInfoData(
                    record.get("latency").asInt(),
                    pathNodes,
                    record.get("speed").asInt(),
                    state,
                    record.get("available_bandwidth").asInt()
            );
            isl.setTimestamp(System.currentTimeMillis());

            results.add(isl);
        }
        return results;
    }

    /**
     * Create the query based on what the strategy is.
     */
    private Statement getPathQuery(Flow flow, Strategy strategy){
        /*
         * TODO: implement strategy
         */


        switch (strategy) {
            case COST:
                return makeCostPathQuery(flow);

            default:
                return makeHopsPathQuery(flow);
        }

    }

    private Statement makeHopsPathQuery(Flow flow) {
        HashMap<String,Value> parameters = new HashMap<>();

        String subject =
                "MATCH (a:switch{name:{src_switch}}),(b:switch{name:{dst_switch}}), " +
                "p = shortestPath((a)-[r:isl*..35]->(b))";
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


    private Statement makeCostPathQuery(Flow flow) {
        HashMap<String,Value> parameters = new HashMap<>();

        String subject =
                "MATCH (from:switch{name:{src_switch}}),(to:switch{name:{dst_switch}}), " +
// (crimi) - unclear how to filter the relationships dijkstra looks out based on properties.
// Probably need to re-write the function and add it to our neo4j container.
//
//                        " CALL apoc.algo.dijkstraWithDefaultWeight(from, to, 'isl', 'cost', 700)" +
//                        " YIELD path AS p, weight AS weight "
                        " p = allShortestPaths((from)-[:isl*..35]->(to)) ";
        parameters.put("src_switch", Values.value(flow.getSourceSwitch()));
        parameters.put("dst_switch", Values.value(flow.getDestinationSwitch()));

        StringJoiner where = new StringJoiner("\n    AND ", "where ", "");
        where.add("ALL(x in nodes(p) WHERE x.state = 'active')");
        if (flow.isIgnoreBandwidth()) {
            where.add("ALL(y in relationships(p) WHERE y.status = 'active')");
        } else {
            where.add("ALL(y in relationships(p) WHERE y.status = 'active' AND y.available_bandwidth >= {bandwidth})");
            parameters.put("bandwidth", Values.value(flow.getBandwidth()));
        }

        String reduce = " WITH REDUCE(cost = 0, rel in rels(p) | " +
                "   cost + CASE rel.cost WHEN rel.cost = 0 THEN 700 ELSE rel.cost END) AS cost, p ";

        String result = "RETURN p ORDER BY cost LIMIT 1";

        String query = String.join("\n", subject, where.toString(), reduce, result);
        return new Statement(query, Values.value(parameters));
    }

}
