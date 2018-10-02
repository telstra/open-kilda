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

package org.openkilda.persistence.repositories.impl;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.Node;
import org.openkilda.model.Path;
import org.openkilda.persistence.repositories.FlowSegmentRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4J OGM implementation of {@link FlowSegmentRepository}.
 */
public class Neo4jFlowSegmentRepository extends Neo4jGenericRepository<FlowSegment> implements FlowSegmentRepository {
    public Neo4jFlowSegmentRepository(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    public Iterable<FlowSegment> findByFlowId(String flowId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("flowid", flowId);

        return getSession().query(FlowSegment.class,
                "MATCH ()-[fs:flow_segment{flowid: {flowid}}]-() RETURN fs", parameters);
    }

    @Override
    public Iterable<FlowSegment> findByFlowIdAndCookie(String flowId, long cookie) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("flow_id", flowId);
        parameters.put("cookie", cookie);
        return getSession().query(FlowSegment.class,
                "MATCH ()-[fs:flow_segment{flowid: {flow_id}, cookie: {cookie}]-() RETURN fs", parameters);
    }

    @Override
    public long deleteFlowSegments(Flow flow) {
        String flowId = flow.getFlowId();
        long flowCookie = flow.getCookie();
        Map<String, Object> parameters = new HashMap<>();
        Filter flowIdFilter = new Filter("flowId", ComparisonOperator.EQUALS, flowId);
        Filter cookieFilter = new Filter("parent_cookie", ComparisonOperator.EQUALS, flowCookie);
        Filters filters = flowIdFilter.and(cookieFilter);
        Long count = (Long) getSession().delete(FlowSegment.class, filters, false);
        return count;
    }

    //NOTE(tdurakov): however it's possible to implement the same functionality with ogm model methods this approach
    //seems to be more efficient, since we do not have switch info by that moment, so it will be additional overhead to
    // grab all switch objects first.
    @Override
    public void mergeFlowSegments(Flow flow) {
        String query = "MERGE "
                + " (src:switch {name: $src_switch}) "
                + "ON CREATE SET src.state='inactive' "
                + "MERGE "
                + " (dst:switch {name: $dst_switch}) "
                + "ON CREATE SET dst.state='inactive' "
                + "MERGE (src) - [fs:flow_segment {"
                + " flowid: $flowid, "
                + " parent_cookie: $parent_cookie "
                + "}] -> (dst) "
                + "SET "
                + " fs.cookie=$cookie, "
                + " fs.src_switch=$src_switch, "
                + " fs.src_port=$src_port, "
                + " fs.dst_switch=$dst_switch, "
                + " fs.dst_port=$dst_port, "
                + " fs.seq_id=$seq_id, "
                + " fs.segment_latency=$segment_latency, "
                + " fs.bandwidth=$bandwidth, "
                + " fs.ignore_bandwidth=$ignore_bandwidth ";
        Path flowPath = flow.getFlowPath();

        Long parentCookie = flow.getCookie();
        List<Node> nodes = flowPath.getNodes();
        for (int i = 0; i < flowPath.getNodes().size(); i += 2) {
            Map<String, Object> parameters = new HashMap<>();

            Node src = nodes.get(i);
            Node dst = nodes.get(i + 1);
            parameters.put("src_switch", src.getSwitchId());
            parameters.put("src_port", src.getPortNo());
            parameters.put("seq_id", src.getSeqId());
            parameters.put("segment_latency", src.getSegmentLatency());
            parameters.put("dst_switch", dst.getSwitchId());
            parameters.put("dst_port", dst.getPortNo());
            parameters.put("ignore_bandwidth", flow.isIgnoreBandwidth());
            parameters.put("flowid", flow.getFlowId());
            parameters.put("parent_cookie", flow.getCookie());
            parameters.put("bandwidth", flow.getBandwidth());
            Long cookie = dst.getCookie();
            if (cookie == null) {
                cookie = parentCookie;
            }
            parameters.put("cookie", cookie);
            getSession().query(FlowSegment.class, query, parameters);
        }
    }

    @Override
    public Iterable<FlowSegment> findByFlowIdAndCookie(String flowId, long cookie) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("flow_id", flowId);
        parameters.put("cookie", cookie);
        return getSession().query(FlowSegment.class,
                "MATCH ()-[fs:flow_segment{flow_id: {flow_id}, cookie: {cookie}]-() RETURN fs", parameters);
    }

    @Override
    public long deleteFlowSegments(Flow flow) {
        String flowId = flow.getFlowId();
        long flowCookie = flow.getCookie();
        Map<String, Object> parameters = new HashMap<>();
        Filter flowIdFilter = new Filter("flow_id", ComparisonOperator.EQUALS, flowId);
        Filter cookieFilter = new Filter("parent_cookie", ComparisonOperator.EQUALS, flowCookie);
        Filters filters = flowIdFilter.and(cookieFilter);
        Long count = (Long) getSession().delete(FlowSegment.class, filters, false);
        return count;
    }

    //NOTE(tdurakov): however it's possible to implement the same functionality with ogm model methods this approach
    //seems to be more efficient, since we do not have switch info by that moment, so it will be additional overhead to
    // grab all switch objects first.
    @Override
    public void mergeFlowSegments(Flow flow) {
        String query = "MERGE "
                + " (src:switch {name: $src_switch}) "
                + "ON CREATE SET src.state='inactive' "
                + "MERGE "
                + " (dst:switch {name: $dst_switch}) "
                + "ON CREATE SET dst.state='inactive' "
                + "MERGE (src) - [fs:flow_segment {"
                + " flow_id: $flowid, "
                + " parent_cookie: $parent_cookie "
                + "}] -> (dst) "
                + "SET "
                + " fs.cookie=$cookie, "
                + " fs.src_switch=$src_switch, "
                + " fs.src_port=$src_port, "
                + " fs.dst_switch=$dst_switch, "
                + " fs.dst_port=$dst_port, "
                + " fs.seq_id=$seq_id, "
                + " fs.segment_latency=$segment_latency, "
                + " fs.bandwidth=$bandwidth, "
                + " fs.ignore_bandwidth=$ignore_bandwidth ";
        Path flowPath = flow.getFlowPath();

        Long parentCookie = flow.getCookie();
        List<Node> nodes = flowPath.getNodes();
        for (int i = 0; i < flowPath.getNodes().size(); i += 2) {
            Map<String, Object> parameters = new HashMap<>();

            Node src = nodes.get(i);
            Node dst = nodes.get(i + 1);
            parameters.put("src_switch", src.getSwitchId());
            parameters.put("src_port", src.getPortNo());
            parameters.put("seq_id", src.getSeqId());
            parameters.put("segment_latency", src.getSegmentLatency());
            parameters.put("dst_switch", dst.getSwitchId());
            parameters.put("dst_port", dst.getPortNo());
            parameters.put("ignore_bandwidth", flow.isIgnoreBandwidth());
            parameters.put("flowid", flow.getFlowId());
            parameters.put("parent_cookie", flow.getCookie());
            parameters.put("bandwidth", flow.getBandwidth());
            Long cookie = dst.getCookie();
            if (cookie == null) {
                cookie = parentCookie;
            }
            parameters.put("cookie", cookie);
            getSession().query(FlowSegment.class, query, parameters);
        }
    }

    @Override
    Class<FlowSegment> getEntityType() {
        return FlowSegment.class;
    }
}
