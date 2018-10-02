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

package org.openkilda.wfm.topology.event;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.NetworkTopologyChange;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.mappers.IslMapper;
import org.openkilda.wfm.share.mappers.PortMapper;
import org.openkilda.wfm.share.mappers.SwitchMapper;
import org.openkilda.wfm.topology.event.service.IslService;
import org.openkilda.wfm.topology.event.service.PortService;
import org.openkilda.wfm.topology.event.service.Sender;
import org.openkilda.wfm.topology.event.service.SwitchService;
import org.openkilda.wfm.topology.reroute.bolts.RerouteBolt;

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class NetworkTopologyBolt extends AbstractBolt {

    private static final Logger logger = LoggerFactory.getLogger(RerouteBolt.class);

    public static final String REROUTE_STREAM = "reroute-stream";

    private transient SwitchService switchService;
    private transient IslService islService;
    private transient PortService portService;

    private PersistenceManager persistenceManager;

    private int islCostWhenPortDown;

    public NetworkTopologyBolt(PersistenceManager persistenceManager, int islCostWhenPortDown) {
        this.persistenceManager = persistenceManager;
        this.islCostWhenPortDown = islCostWhenPortDown;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.switchService = new SwitchService(transactionManager, repositoryFactory);
        this.islService = new IslService(transactionManager, repositoryFactory);
        this.portService = new PortService(transactionManager, repositoryFactory, islCostWhenPortDown);
        super.prepare(stormConf, context, collector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple tuple) {
        String request = tuple.getString(1);

        BaseMessage baseMessage;
        try {
            baseMessage = MAPPER.readValue(request, BaseMessage.class);
        } catch (IOException e) {
            logger.error("Error during parsing request for Reroute topology", e);
            return;
        }

        if (baseMessage instanceof InfoMessage) {
            InfoMessage message = (InfoMessage) baseMessage;
            InfoData data = message.getData();
            Sender sender = new Sender(getOutput(), tuple, message.getCorrelationId());

            if (data instanceof SwitchInfoData) {
                handleSwitchEvents((SwitchInfoData) data);

            } else if (data instanceof IslInfoData) {
                handleIslEvents((IslInfoData) data, sender);

            } else if (data instanceof PortInfoData) {
                handlePortEvents((PortInfoData) data, sender);

            } else if (data instanceof NetworkTopologyChange) {
                handleNetworkTopologyChangeEvents((NetworkTopologyChange) data, sender);

            } else {
                logger.warn("Skip undefined info data type {}", request);
            }

        } else {
            logger.warn("Skip undefined message type {}", request);
        }
    }

    private void handleSwitchEvents(SwitchInfoData data) {
        logger.debug("State update switch {} message {}", data.getSwitchId(), data.getState());

        switch (data.getState()) {

            case ADDED:
                switchService.createOrUpdateSwitch(SwitchMapper.INSTANCE.map(data));
                break;

            case ACTIVATED:
                switchService.activateSwitch(SwitchMapper.INSTANCE.map(data));
                break;

            case DEACTIVATED:
            case REMOVED:
                switchService.deactivateSwitch(SwitchMapper.INSTANCE.map(data));
                break;

            default:
                logger.warn("Unknown state update switch info message");
        }
    }

    private void handleIslEvents(IslInfoData data, Sender sender) {
        logger.debug("State update isl {}. Isl state: {}", data.getId(), data.getState());

        switch (data.getState()) {
            case DISCOVERED:
                boolean success = islService.createOrUpdateIsl(IslMapper.INSTANCE.map(data));

                if (success) {
                    String reason = String.format("Create or update ISL: %s. ISL state: %s",
                            data.getId(), data.getState());
                    sender.sendRerouteInactiveFlowsMessage(reason);
                }
                break;

            case FAILED:
            case MOVED:
                success = islService.islDiscoveryFailed(IslMapper.INSTANCE.map(data));

                if (success) {
                    String reason = String.format("ISL discovery failed: %s. ISL state: %s",
                            data.getId(), data.getState());
                    sender.sendRerouteAffectedFlowsMessage(data.getPath().get(0), reason);
                }
                break;

            default:
                logger.warn("Unknown state update isl info message");
        }
    }


    private void handlePortEvents(PortInfoData data, Sender sender) {
        logger.debug("State update port {}_{} message cached {}",
                data.getSwitchId(), data.getPortNo(), data.getState());

        switch (data.getState()) {
            case DOWN:
            case DELETE:
                boolean success = portService.processWhenPortIsDown(PortMapper.INSTANCE.map(data));

                if (success) {
                    String reason = String.format("Port %s_%s is %s",
                            data.getSwitchId(), data.getPortNo(), data.getState());
                    PathNode pathNode = new PathNode(data.getSwitchId(), data.getPortNo(), 0);
                    sender.sendRerouteAffectedFlowsMessage(pathNode, reason);
                }
                break;

            case UP:
            case ADD:
                break;

            case OTHER_UPDATE:
            case CACHED:
                break;

            default:
                logger.warn("Unknown state update port info message");
                break;
        }
    }

    private void handleNetworkTopologyChangeEvents(NetworkTopologyChange data, Sender sender) {
        logger.debug("Switch flows reroute request");

        switch (data.getType()) {
            case ENDPOINT_DROP:
                return;

            case ENDPOINT_ADD:
                String reason = String.format("Network topology change: %s_%s is %s",
                        data.getSwitchId(), data.getPortNumber(),
                        data.getType());
                sender.sendRerouteInactiveFlowsMessage(reason);
                break;

            default:
                logger.error("Unhandled reroute type: {}", data.getType());
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        declarer.declareStream(REROUTE_STREAM, fields);
    }
}
