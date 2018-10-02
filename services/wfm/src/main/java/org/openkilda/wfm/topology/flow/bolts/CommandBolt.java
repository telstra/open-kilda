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

package org.openkilda.wfm.topology.flow.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.persistence.neo4j.Neo4jConfig;
import org.openkilda.persistence.neo4j.Neo4jTransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.impl.RepositoryFactoryImpl;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;
import org.openkilda.wfm.topology.flow.service.CommandService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class CommandBolt extends BaseRichBolt  {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(CommandBolt.class);


    private CommandService commandService;

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    private Neo4jConfig neo4jConfig;

    public CommandBolt(Neo4jConfig neo4jConfig) {
        this.neo4jConfig = neo4jConfig;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        outputCollector = collector;
        Neo4jTransactionManager transactionManager = new Neo4jTransactionManager(neo4jConfig);
        RepositoryFactory repositoryFactory = new RepositoryFactoryImpl(transactionManager);
        commandService = new CommandService(transactionManager, repositoryFactory);

    }

    @Override
    public void execute(Tuple tuple) {
        InfoMessage msg = (InfoMessage) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        String corrId = msg.getCorrelationId();
        FlowInfoData data = (FlowInfoData) msg.getData();
        switch (streamId) {
            case CREATE:
                Flow flow = data.getPayload().getLeft();
                processCreateFlow(corrId, tuple, flow);
                flow = data.getPayload().getRight();
                processCreateFlow(corrId, tuple, flow);
                break;
            case DELETE:
                flow = data.getPayload().getLeft();
                processDeleteFlow(corrId, tuple, flow);
                flow = data.getPayload().getRight();
                processDeleteFlow(corrId, tuple, flow);
                break;
            default:
                break;
        }



        System.out.println(tuple);
        System.out.println(msg);
    }

    private void processCreateFlow(String correlationId, Tuple tuple, Flow flow) {
        List<BaseInstallFlow> rules = commandService.getInstallRulesForFlow(flow);
        for (int i = rules.size() - 1; i > 0; i--) {
            try {
                BaseInstallFlow rule = rules.get(i);
                CommandMessage message = new CommandMessage(rules.get(i), System.currentTimeMillis(), correlationId);
                message.setDestination(Destination.CONTROLLER);
                Values values = new Values(MAPPER.writeValueAsString(message), rule.getSwitchId(), flow.getFlowId(),
                        rule.getTransactionId());
                outputCollector.emit(StreamType.CREATE.toString(), tuple, values);
            } catch (Exception e) {
                logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
            }

        }
    }

    private void processDeleteFlow(String correlationId, Tuple tuple, Flow flow) {
        List<BaseInstallFlow> rules = commandService.getInstallRulesForFlow(flow);
        for (int i = rules.size() - 1; i > 0; i--) {
            try {
                BaseInstallFlow rule = rules.get(i);
                CommandMessage message = new CommandMessage(rules.get(i), System.currentTimeMillis(), correlationId);
                message.setDestination(Destination.CONTROLLER);
                Values values = new Values(MAPPER.writeValueAsString(message), rule.getSwitchId(), flow.getFlowId(),
                        rule.getTransactionId());
                outputCollector.emit(StreamType.CREATE.toString(), tuple, values);
            } catch (Exception e) {
                logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
            }

        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(
                StreamType.CREATE.toString(),
                FlowTopology.fieldsMessageSwitchIdFlowIdTransactionId
        );
        outputFieldsDeclarer.declareStream(
                StreamType.DELETE.toString(),
                FlowTopology.fieldsMessageSwitchIdFlowIdTransactionId
        );
    }
}
