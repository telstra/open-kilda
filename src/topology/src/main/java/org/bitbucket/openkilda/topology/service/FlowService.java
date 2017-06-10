package org.bitbucket.openkilda.topology.service;

import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;

import java.util.Set;

/**
 * Service for operations on flows.
 */
public interface FlowService {
    /**
     * Creates flow.
     *
     * @param payload       {@link FlowPayload}
     * @param correlationId request correlation Id
     * @return {@link CommandMessage} instances for flow create
     */
    Set<CommandMessage> createFlow(final FlowPayload payload, final String correlationId);

    /**
     * Deletes flow.
     *
     * @param payload       {@link FlowIdStatusPayload}
     * @param correlationId request correlation Id
     * @return {@link CommandMessage} instances for flow delete
     */
    Set<CommandMessage> deleteFlow(final FlowIdStatusPayload payload, final String correlationId);

    /**
     * Updates flow.
     *
     * @param payload       {@link FlowPayload}
     * @param correlationId request correlation Id
     * @return {@link CommandMessage} instances for flow update
     */
    Set<CommandMessage> updateFlow(final FlowPayload payload, final String correlationId);

    /**
     * Gets flow by id.
     *
     * @param payload       {@link FlowIdStatusPayload}
     * @param correlationId request correlation Id
     * @return {@link InfoMessage} instance with flow payload
     */
    InfoMessage getFlow(final FlowIdStatusPayload payload, final String correlationId);

    /**
     * Gets all the flows.
     *
     * @param payload       {@link FlowIdStatusPayload}
     * @param correlationId request correlation Id
     * @return {@link InfoMessage} instance with all flows payload
     */
    InfoMessage getFlows(final FlowIdStatusPayload payload, final String correlationId);

    /**
     * Gets flow path by id.
     *
     * @param payload       {@link FlowIdStatusPayload}
     * @param correlationId request correlation Id
     * @return {@link InfoMessage} instance with flow path payload
     */
    InfoMessage pathFlow(final FlowIdStatusPayload payload, final String correlationId);
}
