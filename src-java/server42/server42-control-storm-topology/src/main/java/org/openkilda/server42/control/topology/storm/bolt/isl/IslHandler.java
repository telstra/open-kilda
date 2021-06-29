/* Copyright 2021 Telstra Open Source
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

package org.openkilda.server42.control.topology.storm.bolt.isl;

import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.server42.control.messaging.flowrtt.Headers;
import org.openkilda.server42.control.messaging.islrtt.AddIsl;
import org.openkilda.server42.control.messaging.islrtt.ClearIsls;
import org.openkilda.server42.control.messaging.islrtt.ListIslPortsOnSwitch;
import org.openkilda.server42.control.topology.service.IslCarrier;
import org.openkilda.server42.control.topology.service.IslRttService;
import org.openkilda.server42.control.topology.storm.ComponentId;
import org.openkilda.server42.control.topology.storm.bolt.isl.command.IslCommand;
import org.openkilda.server42.control.topology.storm.bolt.router.Router;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Set;

public class IslHandler extends AbstractBolt implements IslCarrier {
    public static final String BOLT_ID = ComponentId.ISL_HANDLER.toString();

    public static final String STREAM_CONTROL_COMMANDS_ID = "control.commands";
    public static final Fields STREAM_CONTROL_COMMANDS_FIELDS =
            new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
    private final PersistenceManager persistenceManager;
    private transient IslRttService islRttService;

    public IslHandler(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    protected void init() {
        this.islRttService = new IslRttService(this, persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (Router.BOLT_ID.equals(source)) {
            handleRouterCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(STREAM_CONTROL_COMMANDS_ID, STREAM_CONTROL_COMMANDS_FIELDS);
    }

    private void handleRouterCommand(Tuple input) throws PipelineException {
        handleCommand(input, Router.FIELD_ID_COMMAND);
    }


    private void handleCommand(Tuple input, String fieldName) throws PipelineException {
        IslCommand command = pullValue(input, fieldName, IslCommand.class);
        command.apply(this);
    }

    /**
     * Activate monitoring for all existed ISLs on provided switch.
     *
     * @param switchId specify the ISL endpoint.
     */
    public void processActivateIslMonitoringOnSwitch(SwitchId switchId) {
        islRttService.activateIslMonitoringForSwitch(switchId);
    }

    /**
     * Deactivate monitoring for ISLs on provided switch.
     *
     * @param switchId specify the ISL endpoint.
     */
    public void processDeactivateIslMonitoringOnSwitch(SwitchId switchId) {
        ClearIsls clearIsls = ClearIsls.builder()
                .headers(buildHeader())
                .switchId(switchId)
                .build();
        emit(STREAM_CONTROL_COMMANDS_ID, getCurrentTuple(), new Values(switchId.toString(), clearIsls));
    }

    @Override
    public void notifyActivateIslMonitoring(SwitchId switchId, int port) {
        AddIsl addIsl = AddIsl.builder()
                .headers(buildHeader())
                .switchId(switchId)
                .port(port)
                .build();

        emit(STREAM_CONTROL_COMMANDS_ID, getCurrentTuple(), new Values(switchId.toString(), addIsl));
    }

    public void processSendIslPortListOnSwitchCommand(SwitchId switchId) {
        islRttService.sendIslPortListOnSwitchCommand(switchId);
    }

    @Override
    public void sendListOfIslPortsBySwitchId(SwitchId switchId, Set<Integer> islPorts) {
        ListIslPortsOnSwitch listIslPortsOnSwitch = ListIslPortsOnSwitch.builder()
                .headers(buildHeader())
                .switchId(switchId)
                .islPorts(islPorts)
                .build();
        emit(STREAM_CONTROL_COMMANDS_ID, getCurrentTuple(), new Values(switchId.toString(), listIslPortsOnSwitch));
    }

    private Headers buildHeader() {
        return Headers.builder().correlationId(getCommandContext().getCorrelationId()).build();
    }
}
