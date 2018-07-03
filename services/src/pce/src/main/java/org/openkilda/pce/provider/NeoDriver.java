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

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.algo.SimpleGetShortestPath;
import org.openkilda.pce.api.FlowAdapter;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.model.SimpleIsl;

import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.TransientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class NeoDriver implements PathComputer {
    private static final Logger logger = LoggerFactory.getLogger(NeoDriver.class);

    private final Driver driver;

    public NeoDriver(Driver driver) {
        this.driver = driver;
    }

    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy)
            throws UnroutablePathException, RecoverableException {

        long latency = 0L;
        List<PathNode> forwardNodes = new LinkedList<>();
        List<PathNode> reverseNodes = new LinkedList<>();

        if (! flow.isOneSwitchFlow()) {
            try {
                Pair<LinkedList<SimpleIsl>, LinkedList<SimpleIsl>> biPath = getPathFromNetwork(flow, strategy);
                if (biPath.getLeft().size() == 0 || biPath.getRight().size() == 0) {
                    throw new UnroutablePathException(flow);
                }

                int seqId = 0;
                LinkedList<SimpleIsl> forwardIsl = biPath.getLeft();
                for (SimpleIsl isl : forwardIsl) {
                    latency += isl.latency;
                    forwardNodes.add(new PathNode(isl.src_dpid, isl.src_port, seqId++, (long) isl.latency));
                    forwardNodes.add(new PathNode(isl.dst_dpid, isl.dst_port, seqId++, 0L));
                }

                seqId = 0;
                LinkedList<SimpleIsl> reverseIsl = biPath.getRight();
                for (SimpleIsl isl : reverseIsl) {
                    reverseNodes.add(new PathNode(isl.src_dpid, isl.src_port, seqId++, (long) isl.latency));
                    reverseNodes.add(new PathNode(isl.dst_dpid, isl.dst_port, seqId++, 0L));
                }
            // FIXME(surabujin): Need to catch and trace exact exception thrown in recoverable places.
            } catch (TransientException e) {
                throw new RecoverableException("TransientError from neo4j", e);
            } catch (ClientException e) {
                throw new RecoverableException("ClientException from neo4j", e);
            }
        } else {
            logger.info("No path computation for one-switch flow");
        }

        return new ImmutablePair<>(new PathInfoData(latency, forwardNodes), new PathInfoData(latency, reverseNodes));
    }

    /**
     * Create the query based on what the strategy is.
     */
    private Pair<LinkedList<SimpleIsl>, LinkedList<SimpleIsl>> getPathFromNetwork(Flow flow, Strategy strategy) {
        switch (strategy) {
            default:
                AvailableNetwork network = getAvailableNetwork(flow.isIgnoreBandwidth(), flow.getBandwidth());
                network.removeSelfLoops().reduceByCost();
                SimpleGetShortestPath forward = new SimpleGetShortestPath(
                        network, flow.getSourceSwitch(), flow.getDestinationSwitch(), 35);
                SimpleGetShortestPath reverse = new SimpleGetShortestPath(
                        network, flow.getDestinationSwitch(), flow.getSourceSwitch(), 35);

                LinkedList<SimpleIsl> forwardPath = forward.getPath();
                LinkedList<SimpleIsl> reversePath = reverse.getPath(forwardPath);
                //(crimi) - getPath with hint works .. you can use the next line to troubleshoot if
                // concerned about how hit is working
                //LinkedList<SimpleIsl> rPath = reverse.getPath();
                return Pair.of(forwardPath, reversePath);
        }
    }

    @Override
    public List<FlowInfo> getFlowInfo() {
        List<FlowInfo> flows = new ArrayList<>();
        String subject = "MATCH (:switch)-[f:flow]->(:switch)"
                + "\nRETURN f.flowid as flow_id, "
                + "f.cookie as cookie, "
                + "f.meter_id as meter_id, "
                + "f.transit_vlan as transit_vlan, "
                + "f.src_switch as src_switch";

        Session session = driver.session();
        StatementResult result = session.run(subject);
        for (Record record : result.list()) {
            flows.add(new FlowInfo()
                    .setFlowId(record.get("flow_id").asString())
                    .setSrcSwitchId(record.get("src_switch").asString())
                    .setCookie(record.get("cookie").asLong())
                    .setMeterId(safeAsInt(record.get("meter_id")))
                    .setTransitVlanId(safeAsInt(record.get("transit_vlan")))
            );
        }
        return flows;
    }

    @Override
    public List<Flow> getFlow(String flowId) {
        return getFlows(flowId);
    }

    @Override
    public List<Flow> getFlows(String flowId) {
        String where = "WHERE f.flowid='" + flowId + "'";
        return queryFlows(where);
    }

    @Override
    public List<Flow> getAllFlows() {
        return queryFlows();
    }

    private List<Flow> queryFlows() {
        return this.queryFlows("");
    }

    private List<Flow> queryFlows(String whereClause) {
        // FIXME(surabujin): remove cypher(graphQL) injection breach
        String q = "MATCH (:switch)-[f:flow]->(:switch)"
                + whereClause + "\n"
                + "RETURN f.flowid as flowid, "
                + "f.bandwidth as bandwidth, "
                + "f.ignore_bandwidth as ignore_bandwidth, "
                + "f.cookie as cookie, "
                + "f.description as description, "
                + "f.last_updated as last_updated, "
                + "f.src_switch as src_switch, "
                + "f.dst_switch as dst_switch, "
                + "f.src_port as src_port, "
                + "f.dst_port as dst_port, "
                + "f.src_vlan as src_vlan, "
                + "f.dst_vlan as dst_vlan, "
                + "f.flowpath as path, "
                + "f.meter_id as meter_id, "
                + "f.transit_vlan as transit_vlan";

        logger.debug("Executing getFlows Query: {}", q);

        try (Session session = driver.session()) {
            StatementResult queryResults = session.run(q);
            List<Flow> results = new ArrayList<>();
            for (Record record : queryResults.list()) {
                FlowAdapter adapter = new FlowAdapter(record);
                results.add(adapter.getFlow());
            }
            return results;
        }
    }

    @Override
    public  List<SwitchInfoData> getSwitches() {
        String q = "MATCH (sw:switch)\n"
                + "RETURN sw.name as name, "
                + "sw.address as address, "
                + "sw.hostname as hostname, "
                + "sw.description as description, "
                + "sw.controller as controller, "
                + "sw.state as state\n"
                + "ORDER BY sw.name";

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
     * This will return a network where everything is active and the ISLs have the bandwidth needed.
     * This is a specialization of the general query to get the entire network.
     *
     * <p>NB: If the more common case is required at some point, refactor this into reusing the getAll,
     * then filter the results.
     */
    @Override
    public AvailableNetwork getAvailableNetwork(boolean ignoreBandwidth, int availableBandwidth) {
        String q = "MATCH (src:switch)-[isl:isl]->(dst:switch)\n"
                + "WHERE src.state = 'active'\n"
                + "  AND dst.state = 'active'\n"
                + "  AND isl.status = 'active'\n"
                + "  AND src.name IS NOT NULL AND dst.name IS NOT NULL";
        if (!ignoreBandwidth) {
            q += "\n   AND isl.available_bandwidth >= " + availableBandwidth;
        }
        q += "\nRETURN src.name as src_name, dst.name as dst_name, "
                + "isl.src_port as src_port, "
                + "isl.dst_port as dst_port, "
                + "isl.cost as cost, "
                + "isl.latency as latency"
                + "\nORDER BY src.name";

        logger.debug("Executing getAvailableNetwork Query: {}", q);
        AvailableNetwork network = new AvailableNetwork();
        Session session = driver.session();
        StatementResult queryResults = session.run(q);
        for (Record record : queryResults.list()) {
            network.initOneEntry(
                    record.get("src_name").asString(),
                    record.get("dst_name").asString(),
                    safeAsInt(record.get("src_port")),
                    safeAsInt(record.get("dst_port")),
                    safeAsInt(record.get("cost")),
                    safeAsInt(record.get("latency")));
        }
        return network;
    }

    /**
     * Use crystal ball to guess numeric value read from DB.
     */
    private int safeAsInt(Value val) {
        int asInt = 0;
        if (!val.isNull()) {
            try {
                asInt = Integer.parseInt(val.asObject().toString());
            } catch (Exception e) {
                logger.info("Exception trying to get an Integer; the String isn't parseable. Value: {}", val);
            }
        }
        return asInt;
    }

    @Override
    public List<IslInfoData> getIsls() {

        String q = "MATCH (:switch)-[isl:isl]->(:switch) "
                + "RETURN isl.src_switch as src_switch, "
                + "isl.src_port as src_port, "
                + "isl.dst_switch as dst_switch, "
                + "isl.dst_port as dst_port, "
                + "isl.speed as speed, "
                + "isl.max_bandwidth as max_bandwidth, "
                + "isl.latency as latency, "
                + "isl.available_bandwidth as available_bandwidth, "
                + "isl.status as status order by isl.src_switch";

        logger.debug("Executing getSwitches Query: {}", q);
        Session session = driver.session();
        StatementResult queryResults = session.run(q);
        List<IslInfoData> results = new LinkedList<>();
        for (Record record : queryResults.list()) {
            // max_bandwidth not used in IslInfoData
            final List<PathNode> pathNodes = new ArrayList<>();
            PathNode src = new PathNode();
            src.setSwitchId(record.get("src_switch").asString());
            src.setPortNo(safeAsInt(record.get("src_port")));
            src.setSegLatency(safeAsInt(record.get("latency")));
            pathNodes.add(src);
            PathNode dst = new PathNode();
            dst.setSwitchId(record.get("dst_switch").asString());
            dst.setPortNo(safeAsInt(record.get("dst_port")));
            dst.setSegLatency(safeAsInt(record.get("latency")));
            pathNodes.add(dst);

            String status = record.get("status").asString();
            IslChangeType state = ("active".equals(status)) ? IslChangeType.DISCOVERED : IslChangeType.FAILED;

            IslInfoData isl = new IslInfoData(
                    safeAsInt(record.get("latency")),
                    pathNodes,
                    safeAsInt(record.get("speed")),
                    state,
                    safeAsInt(record.get("available_bandwidth"))
            );
            isl.setTimestamp(System.currentTimeMillis());

            results.add(isl);
        }
        return results;
    }
}
