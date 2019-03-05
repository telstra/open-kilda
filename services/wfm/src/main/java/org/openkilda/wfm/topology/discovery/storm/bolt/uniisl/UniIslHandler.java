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

package org.openkilda.wfm.topology.discovery.storm.bolt.uniisl;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslDataHolder;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.service.DiscoveryUniIslService;
import org.openkilda.wfm.topology.discovery.service.IUniIslCarrier;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.command.IslCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.command.IslDownCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.command.IslMoveCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.command.IslSetupFromHistoryCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.command.IslUpCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.PortHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.speaker.SpeakerRouter;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslCommand;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class UniIslHandler extends AbstractBolt implements IUniIslCarrier {
    public static final String BOLT_ID = ComponentId.UNI_ISL_HANDLER.toString();

    public static final String FIELD_ID_ISL_SOURCE = SpeakerRouter.FIELD_ID_ISL_SOURCE;
    public static final String FIELD_ID_ISL_DEST = SpeakerRouter.FIELD_ID_ISL_DEST;
    public static final String FIELD_ID_COMMAND = SpeakerRouter.FIELD_ID_COMMAND;

    public static final Fields STREAM_FIELDS = SpeakerRouter.STREAM_ISL_FIELDS;

    private transient DiscoveryUniIslService service;

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();
        if (PortHandler.BOLT_ID.equals(source)) {
            handlePortCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handlePortCommand(Tuple input) throws PipelineException {
        UniIslCommand command = pullValue(input, PortHandler.FIELD_ID_COMMAND, UniIslCommand.class);
        command.apply(this);
    }

    @Override
    protected void init() {
        service = new DiscoveryUniIslService(this);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
        // TODO
    }

    @Override
    public void setupIslFromHistory(Endpoint endpoint, IslReference islReference, Isl history) {
        emit(getCurrentTuple(), makeDefaultTuple(new IslSetupFromHistoryCommand(endpoint, islReference, history)));
    }

    @Override
    public void notifyIslUp(Endpoint endpoint, IslReference reference, IslDataHolder islData) {
        emit(getCurrentTuple(), makeDefaultTuple(new IslUpCommand(endpoint, reference, islData)));
    }

    @Override
    public void notifyIslDown(Endpoint endpoint, IslReference reference, boolean isPhysicalDown) {
        emit(getCurrentTuple(), makeDefaultTuple(new IslDownCommand(endpoint, reference, isPhysicalDown)));
    }

    @Override
    public void notifyIslMove(Endpoint endpoint, IslReference reference) {
        emit(getCurrentTuple(), makeDefaultTuple(new IslMoveCommand(endpoint, reference)));
    }

    private Values makeDefaultTuple(IslCommand command) {
        IslReference reference = command.getReference();
        return new Values(reference.getSource(), reference.getDest(), command, getCommandContext());
    }

    // UniIslCommand

    public void processUniIslBfdUpDown(Endpoint endpoint, boolean up) {
        service.uniIslBfdUpDown(endpoint, up);
    }

    public void processUniIslRemove(Endpoint endpoint) {
        service.uniIslRemove(endpoint);
    }

    public void processUniIslSetup(Endpoint endpoint, Isl history) {
        service.uniIslSetup(endpoint, history);
    }

    public void processUniIslPhysicalDown(Endpoint endpoint) {
        service.uniIslPhysicalDown(endpoint);
    }

    public void processUniIslFail(Endpoint endpoint) {
        service.uniIslFail(endpoint);
    }

    public void processUniIslDiscovery(Endpoint endpoint, IslInfoData speakerDiscoveryEvent) {
        service.uniIslDiscovery(endpoint, speakerDiscoveryEvent);
    }
}
