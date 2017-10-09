/* Copyright 2017 Telstra Open Source
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

package org.openkilda.topology.service;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;

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
