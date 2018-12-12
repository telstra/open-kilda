/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.reroute.bolts;

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteAllFlowsForIsl;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.reroute.service.RerouteService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class RerouteBolt extends AbstractBolt {

    private static final Logger logger = LoggerFactory.getLogger(RerouteBolt.class);

    public static final String FLOW_ID_FIELD = "flow-id";
    public static final String CORRELATION_ID_FIELD = "correlation-id";

    private PersistenceManager persistenceManager;
    private transient RerouteService rerouteService;

    public RerouteBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.rerouteService = new RerouteService(repositoryFactory);
        super.prepare(stormConf, context, collector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple tuple) {
        String request = tuple.getString(0);

        CommandMessage message;
        try {
            message = MAPPER.readValue(request, CommandMessage.class);
        } catch (IOException e) {
            logger.error("Error during parsing request for Reroute topology", e);
            return;
        }

        CommandData commandData = message.getData();

        if (commandData instanceof RerouteAffectedFlows) {
            RerouteAffectedFlows rerouteAffectedFlows = (RerouteAffectedFlows) commandData;
            PathNode pathNode = rerouteAffectedFlows.getPathNode();
            Collection<String> affectedFlows
                    = rerouteService.getAffectedFlowIds(pathNode.getSwitchId(), pathNode.getPortNo());

            emitRerouteCommands(tuple, affectedFlows, message.getCorrelationId(),
                    rerouteAffectedFlows.getReason());

        } else if (commandData instanceof RerouteInactiveFlows) {
            RerouteInactiveFlows rerouteInactiveFlows = (RerouteInactiveFlows) commandData;
            Collection<String> inactiveFlows = rerouteService.getInactiveFlows();

            emitRerouteCommands(tuple, inactiveFlows, message.getCorrelationId(),
                    rerouteInactiveFlows.getReason());

        } else if (commandData instanceof RerouteAllFlowsForIsl) {
            RerouteAllFlowsForIsl rerouteAllFlowsForIsl = (RerouteAllFlowsForIsl) commandData;
            PathNode source = rerouteAllFlowsForIsl.getSource();
            PathNode destination = rerouteAllFlowsForIsl.getDestination();
            Collection<String> inactiveFlows =
                    rerouteService.getAllFlowIdsForIsl(source.getSwitchId(), source.getPortNo(),
                                                       destination.getSwitchId(), destination.getPortNo());

            emitRerouteCommands(tuple, inactiveFlows, message.getCorrelationId(),
                    rerouteAllFlowsForIsl.getReason());

        } else {
            logger.warn("Skip undefined message type {}", request);
        }

    }

    private void emitRerouteCommands(Tuple tuple, Collection<String> flows,
                                     String initialCorrelationId, String reason) {
        for (String flowId : flows) {
            String correlationId = format("%s-%s", initialCorrelationId, flowId);

            getOutput().emit(tuple, new Values(flowId, correlationId));

            logger.warn("Flow {} reroute command message sent with correlationId {}, reason \"{}\"",
                    flowId, correlationId, reason);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declare(new Fields(FLOW_ID_FIELD, CORRELATION_ID_FIELD));
    }
}
