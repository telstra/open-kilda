package org.bitbucket.openkilda.northbound.service;

import org.bitbucket.openkilda.northbound.model.Flow;

import java.util.List;

/**
 * Service for operations on flows.
 */
public interface FlowService {
    /**
     * Creates flow.
     *
     * @param flow Flow to create
     * @return Created flow
     */
    Flow create(Flow flow);

    /**
     * Deletes flow.
     *
     * @param id Flow id
     * @return Deleted flow
     */
    Flow delete(String id);

    /**
     * Gets flow by id.
     *
     * @param id Flow id
     * @return Flow
     */
    Flow get(String id);

    /**
     * Updates flow.
     *
     * @param flow New flow
     * @return New flow
     */
    Flow update(Flow flow);

    /**
     * Gets all the flows.
     *
     * @return the list of all flows
     */
    List<Flow> dump();
}
