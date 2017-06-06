package org.bitbucket.openkilda.topology.domain.repository;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;

/**
 * Topology repository.
 * Manages operations on the whole topology.
 */
public interface TopologyRepository extends GraphRepository {
    @Query("MATCH (n) detach delete n")
    void clear();
}
