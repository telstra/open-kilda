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

package org.openkilda.wfm.topology.nbworker.bolts;

import com.google.common.collect.Lists;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.AccessMode;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.messaging.nbtopology.request.ReadDataRequest;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NeoBolt extends BaseRichBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoBolt.class);

    private Driver driver;

    private String address;
    private String login;
    private String password;

    private OutputCollector outputCollector;

    public NeoBolt(String host, String login, String password) {
        this.address = String.format("bolt://%s", host);
        this.login = login;
        this.password = password;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        LOGGER.info("Connecting to neo4j server {}", address);
        driver = GraphDatabase.driver(address, AuthTokens.basic(login, password));
        LOGGER.info("NEO4J connected {} (login=\"{}\")", address, login);
        this.outputCollector = collector;
    }

    @Override
    public void execute(Tuple input) {
        final String streamId = input.getSourceStreamId();
        final String correlationId = input.getString(1);
        LOGGER.debug("Received request with correlationId: {}", correlationId);

        try {
            switch (StreamType.valueOf(streamId)) {
                case READ:
                    ReadDataRequest request = (ReadDataRequest) input.getValue(0);
                    processReadRequest(request, correlationId);
                    break;
                default:
                    LOGGER.warn("Unexpected stream id received: {}", streamId);
            }
        } finally {
            outputCollector.ack(input);
        }
    }

    private void processReadRequest(ReadDataRequest request, String correlationId) {
        LOGGER.debug("Received read request with correlationId {}", correlationId);
        try (Session session = driver.session(AccessMode.READ)) {
            List result = null;
            if (request instanceof GetSwitchesRequest) {
                result = getSwitches(session);
            } else if (request instanceof GetLinksRequest) {
                result = getISLs(session);
            }

            LOGGER.debug("Processed read request with correlationId {}", correlationId);
            outputCollector.emit(Lists.newArrayList(result, correlationId));
        }
    }

    private List<SwitchInfoData> getSwitches(Session session) {
        StatementResult result = session.run("MATCH (sw:switch) " +
                        "RETURN " +
                        "sw.name as name, " +
                        "sw.address as address, " +
                        "sw.hostname as hostname, " +
                        "sw.description as description, " +
                        "sw.controller as controller, " +
                        "sw.state as state " +
                        "order by sw.name");
        List<SwitchInfoData> results = new ArrayList<>();
        for (Record record : result.list()) {
            SwitchInfoData sw = new SwitchInfoData();
            sw.setSwitchId(record.get("name").asString());
            sw.setAddress(record.get("address").asString());
            sw.setController(record.get("controller").asString());
            sw.setDescription(record.get("description").asString());
            sw.setHostname(record.get("hostname").asString());

            String status = record.get("state").asString();
            SwitchState st = "active".equals(status) ? SwitchState.ACTIVATED : SwitchState.DEACTIVATED;
            sw.setState(st);

            results.add(sw);
        }
        LOGGER.info("Result: {}", results.size());

        return results;
    }

    private List<IslInfoData> getISLs(Session session) {
        LOGGER.debug("Getting ISLs...");
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

        StatementResult queryResults = session.run(q);
        List<IslInfoData> results = new ArrayList<>();
        for (Record record : queryResults.list()) {
            LOGGER.debug("Reading record... {}", record);
            // max_bandwidth not used in IslInfoData
            List<PathNode> pathNodes = new ArrayList<>();
            PathNode src = new PathNode();
            src.setSwitchId(record.get("src_switch").asString());
            src.setPortNo(parseValue(record.get("src_port")));
            src.setSegLatency(parseValue(record.get("latency")));
            pathNodes.add(src);
            PathNode dst = new PathNode();
            dst.setSwitchId(record.get("dst_switch").asString());
            dst.setPortNo(parseValue(record.get("dst_port")));
            dst.setSegLatency(parseValue(record.get("latency")));
            pathNodes.add(dst);

            String status = record.get("status").asString();
            IslChangeType state = ("active".equals(status)) ? IslChangeType.DISCOVERED : IslChangeType.FAILED;

            IslInfoData isl = new IslInfoData(
                    parseValue(record.get("latency")),
                    pathNodes,
                    parseValue(record.get("speed")),
                    state,
                    parseValue(record.get("available_bandwidth"))
            );
            isl.setTimestamp(System.currentTimeMillis());

            results.add(isl);
            LOGGER.info("Result ISL: {}", isl);

        }
        return results;
    }


    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("response", "correlationId"));
    }

    @Override
    public void cleanup() {
        driver.close();
    }

    private int parseValue(Value value) {
        return Optional.ofNullable(value)
                .filter(val -> !val.isNull())
                .map(Value::asInt)
                .orElse(0);
    }
}
