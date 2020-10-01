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

package org.openkilda.server42.control.topology.storm.bolt.router;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.FeatureTogglesUpdate;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.server42.control.messaging.flowrtt.ActivateFlowMonitoringInfoData;
import org.openkilda.server42.control.messaging.flowrtt.ActivateFlowMonitoringOnSwitchInfoData;
import org.openkilda.server42.control.messaging.flowrtt.DeactivateFlowMonitoringInfoData;
import org.openkilda.server42.control.messaging.flowrtt.DeactivateFlowMonitoringOnSwitchInfoData;
import org.openkilda.server42.control.topology.service.IRouterCarrier;
import org.openkilda.server42.control.topology.service.RouterService;
import org.openkilda.server42.control.topology.storm.ComponentId;
import org.openkilda.server42.control.topology.storm.bolt.flow.command.ActivateFlowMonitoringCommand;
import org.openkilda.server42.control.topology.storm.bolt.flow.command.ActivateFlowMonitoringOnSwitchCommand;
import org.openkilda.server42.control.topology.storm.bolt.flow.command.DeactivateFlowMonitoringCommand;
import org.openkilda.server42.control.topology.storm.bolt.flow.command.DeactivateFlowMonitoringOnSwitchCommand;
import org.openkilda.server42.control.topology.storm.bolt.flow.command.FlowCommand;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class Router extends AbstractBolt
        implements IRouterCarrier {

    public static final String BOLT_ID = ComponentId.ROUTER.toString();
    public static final String FIELD_ID_COMMAND = "command";
    public static final String FIELD_ID_SWITCH_ID = "switch";
    public static final String FIELD_ID_KEY = MessageKafkaTranslator.FIELD_ID_KEY;
    public static final String FIELD_ID_INPUT = MessageKafkaTranslator.FIELD_ID_PAYLOAD;

    public static final String STREAM_FLOW_ID = "flow";
    public static final Fields STREAM_FLOW_FIELDS = new Fields(FIELD_ID_SWITCH_ID,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    private final PersistenceManager persistenceManager;
    private transient RouterService service;


    public Router(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    protected void init() {
        this.service = new RouterService(this, persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (ComponentId.INPUT_FLOW_HS.toString().equals(source) || ComponentId.INPUT_NB.toString().equals(source)) {
            Message message = pullValue(input, FIELD_ID_INPUT, Message.class);
            flowMessage(input, message);
        } else {
            unhandledInput(input);
        }
    }

    private void flowMessage(Tuple input, Message message) throws PipelineException {
        if (message instanceof InfoMessage) {
            proxyFlow(input, ((InfoMessage) message).getData());
        } else {
            log.error("Do not proxy flow message - unexpected message type \"{}\"", message.getClass());
        }
    }

    private void proxyFlow(Tuple input, InfoData payload) throws PipelineException {
        if (payload instanceof ActivateFlowMonitoringInfoData) {
            ActivateFlowMonitoringInfoData data = (ActivateFlowMonitoringInfoData) payload;
            emit(STREAM_FLOW_ID, input, makeFlowTuple(new ActivateFlowMonitoringCommand(data, true)));
            emit(STREAM_FLOW_ID, input, makeFlowTuple(new ActivateFlowMonitoringCommand(data, false)));
        } else if (payload instanceof DeactivateFlowMonitoringInfoData) {
            DeactivateFlowMonitoringInfoData data = (DeactivateFlowMonitoringInfoData) payload;
            for (SwitchId switchId : data.getSwitchIds()) {
                emit(STREAM_FLOW_ID, input, makeFlowTuple(
                        new DeactivateFlowMonitoringCommand(switchId, data.getFlowId(), true)));
                emit(STREAM_FLOW_ID, input, makeFlowTuple(
                        new DeactivateFlowMonitoringCommand(switchId, data.getFlowId(), false)));
            }
        } else if (payload instanceof ActivateFlowMonitoringOnSwitchInfoData) {
            ActivateFlowMonitoringOnSwitchInfoData data = (ActivateFlowMonitoringOnSwitchInfoData) payload;
            activateFlowMonitoringOnSwitch(data.getSwitchId());
        } else if (payload instanceof DeactivateFlowMonitoringOnSwitchInfoData) {
            DeactivateFlowMonitoringOnSwitchInfoData data = (DeactivateFlowMonitoringOnSwitchInfoData) payload;
            deactivateFlowMonitoringOnSwitch(data.getSwitchId());
        } else if (payload instanceof FeatureTogglesUpdate) {
            FeatureTogglesUpdate data = (FeatureTogglesUpdate) payload;
            this.service.handleFlowRttFeatureToggle(data.getToggles().getServer42FlowRtt());
        } else {
            log.error("Do not proxy flow message - unexpected message payload \"{}\"", payload.getClass());
        }
    }

    private Values makeFlowTuple(FlowCommand command) {
        return new Values(command.getSwitchId(), command, getCommandContext());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(STREAM_FLOW_ID, STREAM_FLOW_FIELDS);
    }

    @Override
    public void activateFlowMonitoringOnSwitch(SwitchId switchId) {
        emit(STREAM_FLOW_ID, getCurrentTuple(), makeFlowTuple(
                new ActivateFlowMonitoringOnSwitchCommand(switchId)));
    }

    @Override
    public void deactivateFlowMonitoringOnSwitch(SwitchId switchId) {
        emit(STREAM_FLOW_ID, getCurrentTuple(), makeFlowTuple(
                new DeactivateFlowMonitoringOnSwitchCommand(switchId)));
    }
}
