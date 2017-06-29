package org.bitbucket.openkilda.topology.domain.repository;

import org.bitbucket.openkilda.topology.domain.Flow;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.Set;

/**
 * Flow repository.
 * Manages operations on flows.
 */
public interface FlowRepository extends GraphRepository<Flow> {
    /**
     * Finds flow by flow id.
     *
     * @param flowId flow id
     * @return reverse and direct {@link Flow} instances as set
     */
    @Query("MATCH ()-[r:flow { flow_id: {flow_id} }]->() return r")
    Set<Flow> findByFlowId(@Param("flow_id") String flowId);

    /**
     * Gets all flows.
     *
     * @return set of all {@link Flow} instances
     */
    @Query("MATCH (n)-[r:flow]->(m) return r")
    Set<Flow> findAll();

    /**
     * Returns set of affected {@link Flow} instances.
     *
     * @param switchId deactivated switch
     * @return set of affected {@link Flow} instances
     */
    @Query("MATCH (n)-[r:flow]-(m) where any(i in r.flow_path where i = {switch}) return r")
    Set<Flow> findFlowsAffectedBySwitch(@Param("switch") final String switchId);
}
