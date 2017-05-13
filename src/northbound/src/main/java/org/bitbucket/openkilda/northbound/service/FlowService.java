package org.bitbucket.openkilda.northbound.service;

import org.bitbucket.openkilda.messaging.payload.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowPathResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsStatusResponsePayload;

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
    FlowPayload deleteFlow(final String id, final String correlationId);

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
     * @param status        target status
     * @param correlationId request correlation id
     * @return the list of all flows with specified status
     */
    FlowsResponsePayload getFlows(final String status, final String correlationId);

    /**
     * Gets flow status by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return flow status
     */
    FlowStatusResponsePayload statusFlow(final String id, final String correlationId);

    /**
     * Gets flows status by status value.
     *
     * @param status        target status
     * @param correlationId request correlation Id
     * @return flow status
     */
    FlowsStatusResponsePayload statusFlows(final String status, final String correlationId);

    /**
     * Gets flow path by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return Flow path
     */
    FlowPathResponsePayload pathFlow(final String id, final String correlationId);
}
