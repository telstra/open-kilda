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
import org.openkilda.messaging.command.reroute.RerouteFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.reroute.StreamType;
import org.openkilda.wfm.topology.reroute.service.RerouteService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

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
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.rerouteService = new RerouteService(transactionManager, repositoryFactory);
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
        if (commandData instanceof RerouteFlows) {

            if (commandData instanceof RerouteAffectedFlows) {
                RerouteAffectedFlows rerouteAffectedFlows = (RerouteAffectedFlows) commandData;
                FlowMapper flowMapper = Mappers.getMapper(FlowMapper.class);
                PathNode pathNode = rerouteAffectedFlows.getPathNode();
                Set<String> affectedFlows = rerouteService.getAffectedFlowIds(flowMapper.map(pathNode));

                emitRerouteCommands(tuple, affectedFlows, message.getCorrelationId(), rerouteAffectedFlows.getReason());

            } else if (commandData instanceof RerouteInactiveFlows) {
                RerouteInactiveFlows rerouteInactiveFlows = (RerouteInactiveFlows) commandData;
                Set<String> inactiveFlows = rerouteService.getInactiveFlows();

                emitRerouteCommands(tuple, inactiveFlows, message.getCorrelationId(), rerouteInactiveFlows.getReason());

            } else {
                logger.warn("Skip undefined message type {}", request);
            }

        } else {
            logger.warn("Skip undefined message type {}", request);
        }

    }

    private void emitRerouteCommands(Tuple tuple, Set<String> flows,
                                     String initialCorrelationId, String reason) {
        for (String flowId : flows) {
            String correlationId = format("%s-%s", initialCorrelationId, flowId);

            getOutput().emit(StreamType.WFM_REROUTE.toString(), tuple, new Values(flowId, correlationId));

            logger.warn("Flow {} reroute command message sent with correlationId {}, reason \"{}\"",
                    flowId, correlationId, reason);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declareStream(StreamType.WFM_REROUTE.toString(), new Fields(FLOW_ID_FIELD, CORRELATION_ID_FIELD));

    }
}
