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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.pce.provider.Auth;
import org.openkilda.wfm.topology.nbworker.converters.LinksConverter;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class LinkOperationsBolt extends NeoOperationsBolt {
    public LinkOperationsBolt(Auth neoAuth) {
        super(neoAuth);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, Session session) {
        List<? extends InfoData> result = null;
        if (request instanceof GetLinksRequest) {
            result = getAllLinks(session);
        } else if (request instanceof LinkPropsGet) {
            result = getLinkProps((LinkPropsGet) request, session);
        } else {
            unhandledInput(tuple);
        }

        return result;
    }

    private List<IslInfoData> getAllLinks(Session session) {
        log.debug("Processing get all links request");
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
        log.debug("Found {} links in the database", results.size());
        return results;
    }

    private List<LinkPropsData> getLinkProps(LinkPropsGet request, Session session) {
        log.debug("Processing get link props request");
        String q = "MATCH (props:link_props) "
                + "WHERE ({src_switch} IS NULL OR props.src_switch={src_switch}) "
                + "AND ({src_port} IS NULL OR props.src_port={src_port}) "
                + "AND ({dst_switch} IS NULL OR props.dst_switch={dst_switch}) "
                + "AND ({dst_port} IS NULL OR props.dst_port={dst_port}) "
                + "RETURN props";

        Map<String, Object> parameters = new HashMap<>();
        String srcSwitch = Optional.ofNullable(request.getSource().getDatapath())
                .map(SwitchId::toString)
                .orElse(null);
        parameters.put("src_switch", srcSwitch);
        parameters.put("src_port", request.getSource().getPortNumber());
        String dstSwitch = Optional.ofNullable(request.getDestination().getDatapath())
                .map(SwitchId::toString)
                .orElse(null);
        parameters.put("dst_switch", dstSwitch);
        parameters.put("dst_port", request.getDestination().getPortNumber());

        StatementResult queryResults = session.run(q, parameters);
        List<LinkPropsData> results = queryResults.list()
                .stream()
                .map(record -> record.get("props"))
                .map(Value::asNode)
                .map(LinksConverter::toLinkPropsData)
                .collect(Collectors.toList());

        log.debug("Found {} link props in the database", results.size());
        return results;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("response", "correlationId"));
    }
}
