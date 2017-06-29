package org.bitbucket.openkilda.topology.service;

import org.bitbucket.openkilda.topology.model.Topology;

/**
 * Topology service interface.
 */
public interface TopologyService {
    /**
     * Cleans topology.
     *
     * @param correlationId request correlation id
     * @return network topology
     */
    Topology clear(final String correlationId);

    /**
     * Dumps topology.
     *
     * @param correlationId request correlation id
     * @return network topology
     */
    Topology network(final String correlationId);
}
