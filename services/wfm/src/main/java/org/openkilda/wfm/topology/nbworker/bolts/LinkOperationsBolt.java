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

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.pce.provider.Auth;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LinkOperationsBolt extends NeoOperationsBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkOperationsBolt.class);

    public LinkOperationsBolt(Auth neoAuth) {
        super(neoAuth);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, Session session) {
        List<? extends InfoData> result = null;
        if (request instanceof GetLinksRequest) {
            result = getAllLinks(session);
        } else {
            unhandledInput(tuple);
        }

        return result;
    }

    private List<IslInfoData> getAllLinks(Session session) {
        LOGGER.debug("Getting ISLs...");
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
                        + "isl.status as status";

        StatementResult queryResults = session.run(q);
        List<IslInfoData> results = new ArrayList<>();
        for (Record record : queryResults.list()) {
            // max_bandwidth not used in IslInfoData
            PathNode src = new PathNode();
            src.setSwitchId(record.get("src_switch").asString());
            src.setPortNo(parseIntValue(record.get("src_port")));
            src.setSegLatency(parseIntValue(record.get("latency")));
            src.setSeqId(0);

            PathNode dst = new PathNode();
            dst.setSwitchId(record.get("dst_switch").asString());
            dst.setPortNo(parseIntValue(record.get("dst_port")));
            dst.setSegLatency(parseIntValue(record.get("latency")));
            dst.setSeqId(1);

            List<PathNode> pathNodes = new ArrayList<>();
            pathNodes.add(src);
            pathNodes.add(dst);

            IslChangeType state = getStatus(record.get("status").asString());
            IslInfoData isl = new IslInfoData(
                    parseIntValue(record.get("latency")),
                    pathNodes,
                    parseIntValue(record.get("speed")),
                    state,
                    parseIntValue(record.get("available_bandwidth"))
            );
            isl.setTimestamp(System.currentTimeMillis());

            results.add(isl);
        }
        LOGGER.debug("Found links: {}", results.size());
        return results;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("response", "correlationId"));
    }

    @Override
    Logger getLogger() {
        return LOGGER;
    }

    private int parseIntValue(Value value) {
        int intValue = 0;
        try {
            intValue = Optional.ofNullable(value)
                    .filter(val -> !val.isNull())
                    .map(Value::asObject)
                    .map(Object::toString)
                    .map(Integer::parseInt)
                    .orElse(0);
        } catch (Exception e) {
            LOGGER.info("Exception trying to get an Integer; the String isn't parseable. Value: {}", value);
        }
        return intValue;
    }

    private IslChangeType getStatus(String status) {
        switch (status) {
            case "active":
                return IslChangeType.DISCOVERED;
            case "inactive":
                return IslChangeType.FAILED;
            case "moved":
                return IslChangeType.MOVED;
            default:
                LOGGER.warn("Found incorrect ISL status: {}", status);
                return IslChangeType.FAILED;
        }
    }



}
