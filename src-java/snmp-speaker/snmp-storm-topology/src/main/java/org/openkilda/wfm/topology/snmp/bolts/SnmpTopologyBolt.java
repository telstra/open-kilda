/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.snmp.bolts;

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.snmp.SnmpTopologyConfig;
import org.openkilda.wfm.topology.snmp.model.SwitchDto;
import org.openkilda.wfm.topology.snmp.model.SwitchToSwitchDtoMapper;
import org.openkilda.wfm.topology.snmp.service.TopologyUpdateService;
import org.openkilda.wfm.topology.snmp.service.UpdateAction;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.Map;

public class SnmpTopologyBolt extends AbstractBolt {

    private final PersistenceManager persistenceManager;
    private final SnmpTopologyConfig config;
    private transient TopologyUpdateService topologyUpdateService;
    private transient SwitchToSwitchDtoMapper mapper;

    public SnmpTopologyBolt(PersistenceManager persistenceManager, SnmpTopologyConfig config) {
        this.persistenceManager = persistenceManager;
        this.config = config;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);

        // do an initial db query to get the list of switches.
        Collection<Switch> activeSwitches = persistenceManager.getRepositoryFactory()
                .createSwitchRepository().findActive();
        log.info("Retrieved active switches list {}", activeSwitches);

        mapper = Mappers.getMapper(SwitchToSwitchDtoMapper.class);
        log.info("Using {} to map Kilda switches for transfer {}", mapper);
        Collection<SwitchDto> dtos = mapper.switchesToSwitchDtos(activeSwitches);
        log.info("Sending switch information to SNMP Collector {}", dtos);


        // we want to update the registered parties only once, even if there might be multiple tasks running.
        // However, if this task restarts and the following code will be executed again. For this reason, on
        // the receiving side, the collector should be idempotent with regard to multiple update of devices
        topologyUpdateService = new TopologyUpdateService(config.getSnmpCollectorTopologyEndpoint(),
                config.getSnmpCollectorUsername(), config.getSnmpCollectorPassword());

        if (context.getThisTaskIndex() == 0) {
            topologyUpdateService.publishTopologyUpdate(UpdateAction.ADD, dtos);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple input) throws Exception {
        // parse the message about the update type, and then call the service. We don't have the message format yet.
        // supposedly the input should have some data that could partition the network, so as to send the update to
        // different collector.
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);
        InfoData data = message.getData();
        // assuming the data will contain the topology update; it should contain at least the action, and device list
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }
}
