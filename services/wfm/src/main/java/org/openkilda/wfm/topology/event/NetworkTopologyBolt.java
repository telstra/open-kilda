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

import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
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

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

public class NetworkTopologyBolt extends AbstractBolt {

    public static final String REROUTE_STREAM = "reroute-stream";

    private transient SwitchService switchService;
    private transient IslService islService;
    private transient PortService portService;

    private PersistenceManager persistenceManager;

    private int islCostWhenPortDown;
    private int islCostWhenUnderMaintenance;

    public NetworkTopologyBolt(PersistenceManager persistenceManager, int islCostWhenPortDown,
                               int islCostWhenUnderMaintenance) {
        this.persistenceManager = persistenceManager;
        this.islCostWhenPortDown = islCostWhenPortDown;
        this.islCostWhenUnderMaintenance = islCostWhenUnderMaintenance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.switchService = new SwitchService(transactionManager, repositoryFactory);
        this.islService = new IslService(transactionManager, repositoryFactory, islCostWhenUnderMaintenance);
        this.portService = new PortService(transactionManager, repositoryFactory, islCostWhenPortDown);
        super.prepare(stormConf, context, collector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple tuple) {
        InfoMessage message = (InfoMessage) tuple.getValueByField(PAYLOAD);

        InfoData data = message.getData();
        Sender sender = new Sender(getOutput(), tuple, message.getCorrelationId());

        if (data instanceof SwitchInfoData) {
            handleSwitchEvents((SwitchInfoData) data);

        } else if (data instanceof IslInfoData) {
            handleIslEvents((IslInfoData) data, sender);

        } else if (data instanceof PortInfoData) {
            handlePortEvents((PortInfoData) data, sender);

        } else {
            log.warn("Skip undefined info data type {}", message);
        }
    }

    private void handleSwitchEvents(SwitchInfoData data) {
        log.debug("State update switch {} message {}", data.getSwitchId(), data.getState());

        if (!switchService.switchIsUnderMaintenance(data.getSwitchId())) {
            switch (data.getState()) {

                case ACTIVATED:
                    switchService.createOrUpdateSwitch(SwitchMapper.INSTANCE.map(data));
                    break;

                case DEACTIVATED:
                    switchService.deactivateSwitch(SwitchMapper.INSTANCE.map(data));
                    break;

                default:
                    log.warn("Unknown state update switch info message");
            }
        }
    }

    private void handleIslEvents(IslInfoData data, Sender sender) {
        log.debug("State update isl {}. Isl state: {}", data.getId(), data.getState());

        switch (data.getState()) {
            case DISCOVERED:
                islService.createOrUpdateIsl(IslMapper.INSTANCE.map(data), sender);
                break;

            case FAILED:
            case MOVED:
                islService.islDiscoveryFailed(IslMapper.INSTANCE.map(data), sender);
                break;

            default:
                log.warn("Unknown state update isl info message");
        }
    }


    private void handlePortEvents(PortInfoData data, Sender sender) {
        log.debug("State update port {}_{} message cached {}",
                data.getSwitchId(), data.getPortNo(), data.getState());

        if (!switchService.switchIsUnderMaintenance(data.getSwitchId())) {
            switch (data.getState()) {
                case DOWN:
                case DELETE:
                    portService.processWhenPortIsDown(PortMapper.INSTANCE.map(data), sender);
                    break;

                default:
                    log.warn("Unknown state update port info message");
                    break;
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        declarer.declareStream(REROUTE_STREAM, fields);
    }
}
