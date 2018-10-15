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

import static org.openkilda.pce.Utils.safeAsInt;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.algo.SimpleGetShortestPath;
import org.openkilda.pce.api.FlowAdapter;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.model.SimpleIsl;

import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.v1.AccessMode;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.TransientException;
import org.neo4j.driver.v1.types.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("squid:S1192")
public class NeoDriver implements PathComputer {
    private static final Logger logger = LoggerFactory.getLogger(NeoDriver.class);

    private final Driver driver;

    public NeoDriver(Driver driver) {
        this.driver = driver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy)
            throws UnroutablePathException, RecoverableException {
        AvailableNetwork network = new AvailableNetwork(driver, flow.isIgnoreBandwidth(), flow.getBandwidth());
        return getPath(flow, network, strategy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPair<PathInfoData, PathInfoData> getPath(Flow flow, AvailableNetwork network, Strategy strategy)
            throws UnroutablePathException, RecoverableException {

        long latency = 0L;
        List<PathNode> forwardNodes = new LinkedList<>();
        List<PathNode> reverseNodes = new LinkedList<>();

        if (!flow.isOneSwitchFlow()) {
            try {
                Pair<LinkedList<SimpleIsl>, LinkedList<SimpleIsl>> biPath = getPathFromNetwork(flow, network, strategy);
                if (biPath.getLeft().size() == 0 || biPath.getRight().size() == 0) {
                    throw new UnroutablePathException(flow);
                }

                int seqId = 0;
                LinkedList<SimpleIsl> forwardIsl = biPath.getLeft();
                for (SimpleIsl isl : forwardIsl) {
                    latency += isl.getLatency();
                    forwardNodes.add(new PathNode(isl.getSrcDpid(), isl.getSrcPort(),
                            seqId++, (long) isl.getLatency()));
                    forwardNodes.add(new PathNode(isl.getDstDpid(), isl.getDstPort(), seqId++, 0L));
                }

                seqId = 0;
                LinkedList<SimpleIsl> reverseIsl = biPath.getRight();
                for (SimpleIsl isl : reverseIsl) {
                    reverseNodes.add(new PathNode(isl.getSrcDpid(), isl.getSrcPort(),
                            seqId++, (long) isl.getLatency()));
                    reverseNodes.add(new PathNode(isl.getDstDpid(), isl.getDstPort(), seqId++, 0L));
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

        return new FlowPair<>(new PathInfoData(latency, forwardNodes), new PathInfoData(latency, reverseNodes));
    }

    /**
     * Create the query based on what the strategy is.
     */
    private Pair<LinkedList<SimpleIsl>, LinkedList<SimpleIsl>> getPathFromNetwork(Flow flow, AvailableNetwork network,
                                                                                  Strategy strategy) {

        switch (strategy) {
            default:
                network.removeSelfLoops().reduceByCost();
                SimpleGetShortestPath forward = new SimpleGetShortestPath(network,
                        flow.getSourceSwitch(), flow.getDestinationSwitch(), 35);
                SimpleGetShortestPath reverse = new SimpleGetShortestPath(network,
                        flow.getDestinationSwitch(), flow.getSourceSwitch(), 35);

                LinkedList<SimpleIsl> forwardPath = forward.getPath();
                LinkedList<SimpleIsl> reversePath = reverse.getPath(forwardPath);
                //(crimi) - getPath with hint works .. you can use the next line to troubleshoot if
                // concerned about how hit is working
                //LinkedList<SimpleIsl> rPath = reverse.getPath();
                return Pair.of(forwardPath, reversePath);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<FlowInfo> getFlowInfo() {
        List<FlowInfo> flows = new ArrayList<>();
        String subject = "MATCH (:switch)-[f:flow]->(:switch) "
                + "RETURN f.flowid as flow_id, "
                + " f.cookie as cookie, "
                + " f.meter_id as meter_id, "
                + " f.transit_vlan as transit_vlan, "
                + " f.src_switch as src_switch";

        try (Session session = driver.session(AccessMode.READ)) {
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
        }
        return flows;
    }

    @Override
    public List<Flow> getFlow(String flowId) {
        return getFlows(flowId);
    }

    @Override
    public List<Flow> getFlows(String flowId) {
        String where = "WHERE f.flowid= $flow_id ";
        Value parameters = Values.parameters("flow_id", flowId);
        return loadFlows(where, parameters);
    }

    @Override
    public List<Flow> getAllFlows() {
        String noWhere = " ";
        return loadFlows(noWhere, null);
    }


    private List<Flow> loadFlows(String whereClause, Value parameters) {
        // FIXME(surabujin): remove cypher(graphQL) injection breach
        String q = ""
                + "MATCH (:switch)-[f:flow]->(:switch)"
                + "\n" + whereClause + "\n"
                + "RETURN f.flowid as flowid,\n"
                + "       f.bandwidth as bandwidth,\n"
                + "       f.ignore_bandwidth as ignore_bandwidth,\n"
                + "       f.periodic_pings as periodic_pings,\n"
                + "       f.cookie as cookie,\n"
                + "       f.description as description,\n"
                + "       f.last_updated as last_updated,\n"
                + "       f.src_switch as src_switch,\n"
                + "       f.dst_switch as dst_switch,\n"
                + "       f.src_port as src_port,\n"
                + "       f.dst_port as dst_port,\n"
                + "       f.src_vlan as src_vlan,\n"
                + "       f.dst_vlan as dst_vlan,\n"
                + "       f.flowpath as path,\n"
                + "       f.meter_id as meter_id,\n"
                + "       f.transit_vlan as transit_vlan,\n"
                + "       f.status as status";

        logger.debug("Executing getFlows Query: {}", q);

        try (Session session = driver.session(AccessMode.READ)) {
            StatementResult queryResults = session.run(q, parameters);
            List<Flow> results = new ArrayList<>();
            for (Record record : queryResults.list()) {
                FlowAdapter adapter = new FlowAdapter(record);
                results.add(adapter.getFlow());
            }
            return results;
        }
    }

    @Override
    public List<SwitchInfoData> getSwitches() {
        String q = "MATCH (sw:switch) RETURN sw ORDER BY sw.name";
        logger.debug("Executing getSwitches Query: {}", q);
        try (Session session = driver.session(AccessMode.READ)) {
            StatementResult queryResults = session.run(q);
            return queryResults.list()
                    .stream()
                    .map(record -> record.get("sw"))
                    .map(Value::asEntity)
                    .map(this::toSwitchInfoData)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public Optional<SwitchInfoData> getSwitchById(SwitchId id) {
        String q = "MATCH (sw:switch) WHERE sw.name = {switchId} RETURN sw ORDER BY sw.name";
        logger.debug("Executing getSwitchById Query: {}", q);
        try (Session session = driver.session(AccessMode.READ)) {
            StatementResult results = session.run(q, Values.parameters("switchId", id.toString()));
            return results.list()
                    .stream()
                    .map(record -> record.get("sw"))
                    .map(Value::asEntity)
                    .map(this::toSwitchInfoData)
                    .findFirst();
        }
    }

    private SwitchInfoData toSwitchInfoData(Entity entity) {
        SwitchInfoData sw = new SwitchInfoData();
        sw.setAddress(entity.get("address").asString());
        sw.setController(entity.get("controller").asString());
        sw.setDescription(entity.get("description").asString());
        sw.setHostname(entity.get("hostname").asString());
        String status = entity.get("state").asString();
        SwitchState st = ("active".equals(status)) ? SwitchState.ACTIVATED : SwitchState.CACHED;
        sw.setState(st);

        sw.setSwitchId(new SwitchId(entity.get("name").asString()));
        return sw;
    }

    @Override
    public List<IslInfoData> getIsls() {

        String q =
                "MATCH (:switch)-[isl:isl]->(:switch) "
                        + "RETURN "
                        + "isl.src_switch as src_switch, "
                        + "isl.src_port as src_port, "
                        + "isl.dst_switch as dst_switch, "
                        + "isl.dst_port as dst_port, "
                        + "isl.speed as speed, "
                        + "isl.max_bandwidth as max_bandwidth, "
                        + "isl.latency as latency, "
                        + "isl.available_bandwidth as available_bandwidth, "
                        + "isl.status as status "
                        + "order by isl.src_switch";

        logger.debug("Executing getSwitches Query: {}", q);
        try (Session session = driver.session(AccessMode.READ)) {

            StatementResult queryResults = session.run(q);
            List<IslInfoData> results = new LinkedList<>();
            for (Record record : queryResults.list()) {
                // max_bandwidth not used in IslInfoData
                PathNode src = new PathNode();
                src.setSwitchId(new SwitchId(record.get("src_switch").asString()));
                src.setPortNo(safeAsInt(record.get("src_port")));
                src.setSegLatency(safeAsInt(record.get("latency")));

                List<PathNode> pathNodes = new ArrayList<>();
                pathNodes.add(src);

                PathNode dst = new PathNode();
                dst.setSwitchId(new SwitchId(record.get("dst_switch").asString()));
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

    @Override
    public AvailableNetwork getAvailableNetwork(boolean ignoreBandwidth, long requestedBandwidth) {
        return new AvailableNetwork(driver, ignoreBandwidth, requestedBandwidth);
    }
}
