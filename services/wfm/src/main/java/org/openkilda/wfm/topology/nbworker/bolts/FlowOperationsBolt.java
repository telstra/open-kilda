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
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowIdsForLinkRequest;
import org.openkilda.pce.provider.Auth;
import org.openkilda.wfm.error.IslExistsException;
import org.openkilda.wfm.topology.nbworker.converters.LinksConverter;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FlowOperationsBolt extends NeoOperationsBolt {

    private static final Logger logger = LoggerFactory.getLogger(FlowOperationsBolt.class);

    public FlowOperationsBolt(Auth neoAuth) {
        super(neoAuth);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, Session session) {
        List<? extends InfoData> result = null;
        if (request instanceof GetFlowIdsForLinkRequest) {
            result = getFlowIdsForLink((GetFlowIdsForLinkRequest) request, session);
        } else {
            unhandledInput(tuple);
        }

        return result;
    }

    private List<FlowsResponse> getFlowIdsForLink(GetFlowIdsForLinkRequest request, Session session) {
        SwitchId sourceSwitchId = request.getSource().getDatapath();
        Integer sourcePort = request.getSource().getPortNumber();
        SwitchId destinationSwitchId = request.getDestination().getDatapath();
        Integer destinationPort = request.getDestination().getPortNumber();

        if (sourceSwitchId == null || sourcePort == null || destinationSwitchId == null || destinationPort == null) {
            logger.error("Not all parameters are specified.");
            throw  new IllegalArgumentException("Not all parameters are specified.");
        }

        List<IslInfoData> islInfoData = getAllLinks(session);
        List<PathNode> pathNode = new ArrayList<>();
        pathNode.add(new PathNode(sourceSwitchId, sourcePort, 0));
        pathNode.add(new PathNode(destinationSwitchId, destinationPort, 0));

        boolean pathNodeMatch = islInfoData.stream().map(PathInfoData::getPath).anyMatch(p -> p.equals(pathNode));

        if (!pathNodeMatch) {
            logger.error("There is no ISL between {}-{} and {}-{}.",
                    sourceSwitchId, sourcePort, destinationSwitchId, destinationPort);
            throw new IslExistsException(String.format("There is no ISL between %s-%d and %s-%d.",
                    sourceSwitchId, sourcePort, destinationSwitchId, destinationPort));
        }

        logger.debug("Processing get all flow ids for link request");
        String q = "MATCH (:switch)-[fc:flow_segment]->(:switch)"
                + "WHERE fc.src_switch={src_switch} "
                + "AND fc.src_port={src_port} "
                + "AND fc.dst_switch={dst_switch} "
                + "AND fc.dst_port={dst_port} "
                + "RETURN fc.flowid as flowid";

        Map<String, Object> parameters = new HashMap<>();
        String srcSwitch = Optional.ofNullable(sourceSwitchId)
                .map(SwitchId::toString)
                .orElse(null);
        parameters.put("src_switch", srcSwitch);
        parameters.put("src_port", sourcePort);
        String dstSwitch = Optional.ofNullable(destinationSwitchId)
                .map(SwitchId::toString)
                .orElse(null);
        parameters.put("dst_switch", dstSwitch);
        parameters.put("dst_port", destinationPort);

        StatementResult queryResults = session.run(q, parameters);
        List<String> flowIds = queryResults.list()
                .stream()
                .map(record -> record.get("flowid"))
                .map(Value::asString)
                .collect(Collectors.toList());

        logger.debug("Found {} flow ids for this link in the database", flowIds.size());

        return Collections.singletonList(new FlowsResponse(flowIds));
    }

    private List<IslInfoData> getAllLinks(Session session) {
        logger.debug("Processing get all links request");
        String q =
                "MATCH (:switch)-[isl:isl]->(:switch) "
                        + "RETURN isl";

        StatementResult queryResults = session.run(q);
        List<IslInfoData> results = queryResults.list()
                .stream()
                .map(record -> record.get("isl"))
                .map(Value::asRelationship)
                .map(LinksConverter::toIslInfoData)
                .collect(Collectors.toList());
        logger.debug("Found {} links in the database", results.size());
        return results;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("response", "correlationId"));
    }

    @Override
    Logger getLogger() {
        return logger;
    }

}
