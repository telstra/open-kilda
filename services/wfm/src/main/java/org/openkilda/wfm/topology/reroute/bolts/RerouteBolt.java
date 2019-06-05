/* Copyright 2019 Telstra Open Source
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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowPathSwapRequest;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.reroute.RerouteTopology;
import org.openkilda.wfm.topology.reroute.StreamType;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.service.RerouteService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Slf4j
public class RerouteBolt extends AbstractBolt {

    public static final String FLOW_ID_FIELD = "flow-id";
    public static final String THROTTLING_DATA_FIELD = "throttling-data";

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
    protected void handleInput(Tuple tuple) throws PipelineException {
        CommandMessage message = pullValue(tuple, MessageTranslator.FIELD_ID_PAYLOAD, CommandMessage.class);
        CommandData commandData = message.getData();

        if (commandData instanceof RerouteAffectedFlows) {
            RerouteAffectedFlows rerouteAffectedFlows = (RerouteAffectedFlows) commandData;
            PathNode pathNode = rerouteAffectedFlows.getPathNode();
            Collection<FlowPath> affectedFlowPaths
                    = rerouteService.getAffectedFlowPaths(pathNode.getSwitchId(), pathNode.getPortNo());

            // swapping affected primary paths with available protected
            List<FlowPath> pathsForSwapping = rerouteService.getPathsForSwapping(affectedFlowPaths);
            emitPathSwapCommands(tuple, pathsForSwapping, message.getCorrelationId(),
                    rerouteAffectedFlows.getReason());

            Map<Flow, Set<PathId>> flowsForRerouting = rerouteService.groupFlowsForRerouting(affectedFlowPaths);
            for (Entry<Flow, Set<PathId>> entry : flowsForRerouting.entrySet()) {

                emitRerouteCommand(tuple, message.getCorrelationId(), entry.getKey(), entry.getValue(),
                        rerouteAffectedFlows.getReason());
            }

        } else if (commandData instanceof RerouteInactiveFlows) {
            RerouteInactiveFlows rerouteInactiveFlows = (RerouteInactiveFlows) commandData;
            Map<Flow, Set<PathId>> flowsForRerouting = rerouteService.getInactiveFlowsForRerouting();

            for (Entry<Flow, Set<PathId>> entry : flowsForRerouting.entrySet()) {

                emitRerouteCommand(tuple, message.getCorrelationId(), entry.getKey(), entry.getValue(),
                        rerouteInactiveFlows.getReason());
            }

        } else {
            log.warn("Skip undefined message type {}", message);
        }

    }

    private void emitRerouteCommand(Tuple tuple, String correlationId, Flow flow, Set<PathId> paths, String reason) {
        getOutput().emit(tuple, new Values(flow.getFlowId(),
                new FlowThrottlingData(correlationId, flow.getPriority(), flow.getTimeCreate(), paths)));

        log.warn("Flow {} reroute command message sent with correlationId {}, reason \"{}\"",
                flow.getFlowId(), correlationId, reason);

    }

    private void emitPathSwapCommands(Tuple tuple, Collection<FlowPath> paths,
                                     String correlationId, String reason) {
        for (FlowPath path : paths) {
            FlowPathSwapRequest request = new FlowPathSwapRequest(path.getFlow().getFlowId(), path.getPathId());
            getOutput().emit(StreamType.SWAP.toString(), tuple, new Values(correlationId,
                    new CommandMessage(request, System.currentTimeMillis(), correlationId)));

            log.warn("Flow {} swap path command message sent with correlationId {}, reason \"{}\"",
                    path.getFlow().getFlowId(), correlationId, reason);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declare(new Fields(FLOW_ID_FIELD, THROTTLING_DATA_FIELD));
        output.declareStream(StreamType.SWAP.toString(), RerouteTopology.KAFKA_FIELDS);
    }
}
