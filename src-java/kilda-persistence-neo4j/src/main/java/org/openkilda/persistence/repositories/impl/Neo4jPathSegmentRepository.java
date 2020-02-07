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

import static java.lang.String.format;

import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.PathIdConverter;
import org.openkilda.persistence.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.PathSegmentRepository;

import org.neo4j.ogm.session.Neo4jSession;
import org.neo4j.ogm.session.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4j OGM implementation of {@link PathSegmentRepository}.
 */
public class Neo4jPathSegmentRepository extends Neo4jGenericRepository<PathSegment> implements PathSegmentRepository {
    private final SwitchIdConverter switchIdConverter = new SwitchIdConverter();
    private final PathIdConverter pathIdConverter = new PathIdConverter();

    public Neo4jPathSegmentRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public void updateFailedStatus(PathId pathId, PathSegment segment, boolean failed) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src_switch", switchIdConverter.toGraphProperty(segment.getSrcSwitch().getSwitchId()));
        parameters.put("src_port", segment.getSrcPort());
        parameters.put("dst_switch", switchIdConverter.toGraphProperty(segment.getDestSwitch().getSwitchId()));
        parameters.put("dst_port", segment.getDestPort());
        parameters.put("path_id", pathIdConverter.toGraphProperty(pathId));
        parameters.put("failed", failed);

        Session session = getSession();
        Optional<Long> updatedEntityId = queryForLong(
                "MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                        + "WHERE src.name = $src_switch AND ps.src_port = $src_port  "
                        + "AND dst.name = $dst_switch AND ps.dst_port = $dst_port "
                        + "MATCH (fp:flow_path {path_id: $path_id})-[:owns]-(ps) "
                        + "SET ps.failed=$failed "
                        + "RETURN id(ps) as id", parameters, "id");
        if (!updatedEntityId.isPresent()) {
            throw new PersistenceException(format("PathSegment not found to be updated: %s_%d - %s_%d. Path id: %s.",
                    segment.getSrcSwitch().getSwitchId(), segment.getSrcPort(),
                    segment.getDestSwitch().getSwitchId(), segment.getDestPort(), pathId));
        }

        Object updatedEntity = ((Neo4jSession) session).context().getNodeEntity(updatedEntityId.get());
        if (updatedEntity instanceof PathSegment) {
            PathSegment updatedPathSegment = (PathSegment) updatedEntity;
            updatedPathSegment.setFailed(failed);
        } else if (updatedEntity != null) {
            throw new PersistenceException(format("Expected a PathSegment entity, but found %s.", updatedEntity));
        }
    }

    @Override
    protected Class<PathSegment> getEntityType() {
        return PathSegment.class;
    }
}
