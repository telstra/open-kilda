package org.bitbucket.openkilda.topology.domain.repository;

import org.bitbucket.openkilda.topology.domain.Switch;

import org.springframework.data.neo4j.repository.GraphRepository;

/**
 * Switch repository.
 * Manages operations on switches.
 */
public interface SwitchRepository extends GraphRepository<Switch> {
    /**
     * Finds switch by name.
     *
     * @param name switch datapath id
     * @return {@link Switch} instance
     */
    Switch findByDpid(String name);
}
