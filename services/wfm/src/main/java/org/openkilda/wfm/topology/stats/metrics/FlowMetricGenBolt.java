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

package org.openkilda.wfm.topology.stats.metrics;

import static org.neo4j.helpers.collection.MapUtil.map;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;
import org.neo4j.helpers.collection.Iterators;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.wfm.topology.FlowCookieException;
import org.openkilda.wfm.topology.stats.CypherExecutor;
import org.openkilda.wfm.topology.stats.FlowResult;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.StatsStreamType;

import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * The type Flow metric gen bolt.
 */
public class FlowMetricGenBolt extends MetricGenBolt {
    private static final String GET_ALL_FLOWS = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r";

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowMetricGenBolt.class);
    private final Map<Long, FlowResult> cookieMap = new HashMap<>();
    private CypherExecutor cypher;
//    private final String neoUri;


    public FlowMetricGenBolt() {
//        neoUri = "bolt://localhost:7687";
    }
    /**
     * Instantiates a new Flow metric gen bolt.
     *
     * @param neo4jHost   the neo 4 j host
     * @param neo4jUser   the neo 4 j user
     * @param neo4jPasswd the neo 4 j passwd
     */
    public FlowMetricGenBolt(String neo4jHost, String neo4jUser, String neo4jPasswd) {
//        neoUri = String.format("bolt://%s:%s@%s", neo4jUser, neo4jPasswd, neo4jHost);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;

        // Create a hashmap of cookies to flows such that we can add the flowid to the tsdb stat
//        try {
//            cypher = createCypherExecutor(neoUri);
//            warmCookieMap();
//        } catch (ServiceUnavailableException | IllegalArgumentException e) {
//            LOGGER.error("Error connecting to nego4j", e);
//        }
    }

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        LOGGER.debug("Flow stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId, StatsStreamType.valueOf(input.getSourceStreamId()));
        FlowStatsData data = (FlowStatsData) message.getData();
        long timestamp = message.getTimestamp();
        String switchId = data.getSwitchId().replaceAll(":", "");

        try {
            for (FlowStatsReply reply : data.getStats()) {
                for (FlowStatsEntry entry : reply.getEntries()) {
                    emit(entry, timestamp, switchId);
                }
            }
            collector.ack(input);
        } catch (ServiceUnavailableException e) {
            LOGGER.error("Error process: {}", input.toString(), e);
            collector.ack(input); // If we can't connect to Neo then don't know if valid input, but if NEO is down puts a loop to
                                  // kafka, so fail the request.
        } catch (Exception e) {
            collector.ack(input); // We tried, no need to try again
        }
    }

    private void emit(FlowStatsEntry entry, long timestamp, String switchId) throws Exception {
//        FlowResult flow = getFlowFromCache(entry.getCookie());
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId);
        tags.put("cookie", String.valueOf(entry.getCookie()));
        tags.put("tableid", String.valueOf(entry.getTableId()));
//        if (flow == null || flow.getFlowId() == null) {
//            tags.put("flowid", "unknown");
//        } else {
//            tags.put("flowid", flow.getFlowId());
//        }
        collector.emit(tuple("pen.flow.raw.packets", timestamp, entry.getPacketCount(), tags));
        collector.emit(tuple("pen.flow.raw.bytes", timestamp, entry.getByteCount(), tags));
        collector.emit(tuple("pen.flow.raw.bits", timestamp, entry.getByteCount() * 8, tags));

//        /**
//         * If this is the destination switch for the flow, then add to TSDB for pen.flow.* stats.  This is needed
//         * as there is needed to provide simple lookup of flow stats
//         **/
//        if (switchId.equals(flow.getDstSw().replaceAll(":", ""))) {
//            tags.remove("cookie");  //Doing this to prevent creation of yet another object
//            tags.remove("tableid");
//            tags.remove("switchid");
//            tags.put("direction", flow.getDirection());
//            collector.emit(tuple("pen.flow.packets", timestamp, entry.getPacketCount(), tags));
//            collector.emit(tuple("pen.flow.bytes", timestamp, entry.getByteCount(), tags));
//            collector.emit(tuple("pen.flow.bits", timestamp, entry.getByteCount() * 8, tags));
//        }
    }


    /**
     * Create a CypherExecutor
     *
     * @param uri the URI of the neo4J server
     * @return
     */
    private CypherExecutor createCypherExecutor(String uri) {
        try {
            String auth = new URL(uri.replace("bolt", "http")).getUserInfo();
            if (auth != null) {
                String[] parts = auth.split(":");
                return new CypherExecutor(uri, parts[0], parts[1]);
            }
            return new CypherExecutor(uri);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Neo4j-ServerURL " + uri);
        }
    }

    /**
     * Pre-Populate the cookieMap from Neo4J
     */
    @SuppressWarnings("unchecked")
    private void warmCookieMap() {
        Iterator<Map<String, Object>> result;
        try {
            result = cypher.query(GET_ALL_FLOWS, null);
        } catch (ClientException e) {
            LOGGER.error("Error warming cookieMap", e);
            return;
        }
        while (result.hasNext()) {
            Map<String, Object> row = result.next();
            FlowResult flow;
            try {
                flow = new FlowResult(row);
                cookieMap.put(flow.getCookie(), flow);
                LOGGER.debug("added entry to cookieMap: {}", flow.toString());
            } catch (FlowCookieException e) {
                LOGGER.error("error processing cookie", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected FlowResult getFlowFromCache(Long cookie) throws Exception {
        FlowResult flow = cookieMap.get(cookie);
        if (flow == null) {
            LOGGER.info("{} not found, fetching.", cookie);
            try {
                flow = new FlowResult((Map) getFlowWithCookie(cookie).get("r"));
            } catch (ClientException e) {
                LOGGER.error("error getting flow for {}", cookie);
            }
            cookieMap.put(flow.getCookie(), flow);
            LOGGER.debug("added entry to cookieMap: {}", flow.toString());
        }
        return flow;
    }

    protected Map getFlowWithCookie(Long cookie) {
        if (cookie == null) return Collections.emptyMap();
        return Iterators.singleOrNull(cypher.query(
                "MATCH (a:switch)-[r:flow {cookie:{cookie}}]->(b:switch) RETURN r",
                map("cookie", cookie)));
    }
}