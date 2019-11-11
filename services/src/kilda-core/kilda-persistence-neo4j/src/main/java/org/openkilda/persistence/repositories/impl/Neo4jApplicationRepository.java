/* Copyright 2019 Telstra Open Source
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

import org.openkilda.model.ApplicationRule;
import org.openkilda.model.Metadata;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.ApplicationRepository;

import com.google.common.collect.Lists;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Neo4jApplicationRepository extends Neo4jGenericRepository<ApplicationRule>
        implements ApplicationRepository {
    public Neo4jApplicationRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    protected Class<ApplicationRule> getEntityType() {
        return ApplicationRule.class;
    }

    @Override
    public Optional<ApplicationRule> lookupRuleByMatchAndFlow(SwitchId switchId, String flowId, String srcIp,
                                                              Integer srcPort, String dstIp, Integer dstPort,
                                                              String proto, String ethType, Metadata metadata) {
        Map<String, Object> parameters =
                getBaseParameters(switchId, srcIp, srcPort, dstIp, dstPort, proto, ethType, metadata);
        parameters.put("flow_id", flowId);

        String query = "MATCH (ar:application_rule) "
                + "WHERE ar.switch_id = $switch_id "
                + " AND ar.flow_id = $flow_id "
                + " AND ar.src_ip = $src_ip "
                + " AND ar.src_port = $src_port "
                + " AND ar.dst_ip = $dst_ip "
                + " AND ar.dst_port = $dst_port "
                + " AND ar.proto = $proto "
                + " AND ar.eth_type = $eth_type "
                + " AND ar.metadata = $metadata "
                + "RETURN ar";

        return processQuery(parameters, query);
    }

    @Override
    public Optional<ApplicationRule> lookupRuleByMatchAndCookie(SwitchId switchId, Long cookie, String srcIp,
                                                                Integer srcPort, String dstIp, Integer dstPort,
                                                                String proto, String ethType, Metadata metadata) {
        Map<String, Object> parameters =
                getBaseParameters(switchId, srcIp, srcPort, dstIp, dstPort, proto, ethType, metadata);
        parameters.put("cookie", cookie);

        String query = "MATCH (ar:application_rule) "
                + "WHERE ar.switch_id = $switch_id "
                + " AND ar.cookie = $cookie "
                + " AND ar.src_ip = $src_ip "
                + " AND ar.src_port = $src_port "
                + " AND ar.dst_ip = $dst_ip "
                + " AND ar.dst_port = $dst_port "
                + " AND ar.proto = $proto "
                + " AND ar.eth_type = $eth_type "
                + " AND ar.metadata = $metadata "
                + "RETURN ar";

        return processQuery(parameters, query);
    }

    @Override
    public Collection<ApplicationRule> findBySwitchId(SwitchId switchId) {
        return loadAll(new Filter("switch_id", ComparisonOperator.EQUALS, switchId));
    }

    @Override
    public Collection<ApplicationRule> findByFlowId(String flowId) {
        return loadAll(new Filter("flow_id", ComparisonOperator.EQUALS, flowId));
    }

    private Map<String, Object> getBaseParameters(SwitchId switchId, String srcIp, Integer srcPort, String dstIp,
                                                  Integer dstPort, String proto, String ethType, Metadata metadata) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("switch_id", switchId.toString());
        parameters.put("src_ip", srcIp);
        parameters.put("src_port", srcPort);
        parameters.put("dst_ip", dstIp);
        parameters.put("dst_port", dstPort);
        parameters.put("proto", proto);
        parameters.put("eth_type", ethType);
        parameters.put("metadata", metadata.getValue());

        return parameters;
    }

    private Optional<ApplicationRule> processQuery(Map<String, Object> parameters, String query) {
        Collection<ApplicationRule> results = Lists.newArrayList(
                getSession().query(getEntityType(), query, parameters));

        if (results.size() > 1) {
            throw new PersistenceException("Found more that 1 Application Rule entity by criteria");
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.iterator().next());
    }
}
