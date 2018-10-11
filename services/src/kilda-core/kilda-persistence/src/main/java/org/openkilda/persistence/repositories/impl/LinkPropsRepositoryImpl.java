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

import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.neo4j.Neo4jSessionFactory;
import org.openkilda.persistence.repositories.LinkPropsRepository;

import java.util.HashMap;
import java.util.Map;

public class LinkPropsRepositoryImpl extends GenericRepository<LinkProps> implements LinkPropsRepository {

    public LinkPropsRepositoryImpl(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    Class<LinkProps> getEntityType() {
        return LinkProps.class;
    }

    @Override
    public LinkProps findByNodes(int srcPort, SwitchId srcSwitch, int dstPort, SwitchId dstSwitch) {
        Map<String, Object> params = new HashMap<>();
        params.put("src_port", srcPort);
        params.put("dst_port", dstPort);
        params.put("src_switch", srcSwitch);
        params.put("dst_switch", dstSwitch);
        String query = "MATCH (f:link_props{src_port: {src_port}, dst_port: {dst_port}, src_switch: {src_switch}, "
                + "dst_switch: {dst_switch}}) RETURN f";
        return getSession().queryForObject(LinkProps.class, query, params);
    }
}
