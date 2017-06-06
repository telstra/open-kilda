package org.bitbucket.openkilda.northbound.service;

import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowsPayload;

/**
 * FlowService is for operations on flows.
 */
public interface FlowService extends BasicService {
    /**
     * Creates flow.
     *
     * @param flow          flow
     * @param correlationId request correlation Id
     * @return created flow
     */
    FlowPayload createFlow(final FlowPayload flow, final String correlationId);

    /**
     * Deletes flow.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return deleted flow
     */
    FlowIdStatusPayload deleteFlow(final String id, final String correlationId);

    /**
     * Updates flow.
     *
     * @param flow          flow
     * @param correlationId request correlation Id
     * @return updated flow
     */
    FlowPayload updateFlow(final FlowPayload flow, final String correlationId);

    /**
     * Gets flow by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return flow
     */
    FlowPayload getFlow(final String id, final String correlationId);

    /**
     * Gets all the flows.
     *
     * @param correlationId request correlation id
     * @return the list of all flows with specified status
     */
    FlowsPayload getFlows(final String correlationId);

    /**
     * Gets flow status by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return flow status
     */
    FlowIdStatusPayload statusFlow(final String id, final String correlationId);

    /**
     * Gets flow path by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return Flow path
     */
    FlowPathPayload pathFlow(final String id, final String correlationId);
}
