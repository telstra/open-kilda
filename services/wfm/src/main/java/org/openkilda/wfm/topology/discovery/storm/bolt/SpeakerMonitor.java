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

package org.openkilda.wfm.topology.discovery.storm.bolt;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.info.event.IslBfdFlagUpdated;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.command.IslBfdFlagUpdatedCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.command.IslCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.command.PortEventCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.command.SwitchCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.command.SwitchEventCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.command.SwitchUnmanagedEventCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.command.WatcherCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.command.WatcherSpeakerDiscoveryCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.command.WatcherSpeakerSendConfirmationCommand;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class SpeakerMonitor extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SPEAKER_MONITOR.toString();

    public static final String FIELD_ID_INPUT = MessageTranslator.FIELD_ID_PAYLOAD;
    public static final String FIELD_ID_DATAPATH = "switch";
    public static final String FIELD_ID_PORT_NUMBER = "port-number";
    public static final String FIELD_ID_ISL_SOURCE = "isl-source";
    public static final String FIELD_ID_ISL_DEST = "isl-dest";
    public static final String FIELD_ID_COMMAND = "command";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_WATCHER_ID = "watcher";
    public static final Fields STREAM_WATCHER_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_ISL_ID = "isl";
    public static final Fields STREAM_ISL_FIELDS = new Fields(FIELD_ID_ISL_SOURCE, FIELD_ID_ISL_DEST,
                                                              FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();
        if (ComponentId.INPUT_SPEAKER.toString().equals(source)) {
            Message message = pullValue(input, FIELD_ID_INPUT, Message.class);
            speakerMessage(input, message);
        } else {
            unhandledInput(input);
        }
    }

    private void speakerMessage(Tuple input, Message message) {
        proxySpeaker(input, message);
    }

    private void proxySpeaker(Tuple input, Message message) {
        if (message instanceof InfoMessage) {
            proxySpeaker(input, ((InfoMessage) message).getData());
        } else {
            log.error("Do not proxy speaker message - unexpected message type \"{}\"", message.getClass());
        }
    }

    private void proxySpeaker(Tuple input, InfoData payload) {
        try {
            if (payload instanceof IslInfoData) {
                emit(STREAM_WATCHER_ID, input,
                        makeWatcherTuple(input, new WatcherSpeakerDiscoveryCommand((IslInfoData) payload)));
            } else if (payload instanceof DiscoPacketSendingConfirmation) {
                emit(STREAM_WATCHER_ID, input,
                        makeWatcherTuple(input,
                                new WatcherSpeakerSendConfirmationCommand((DiscoPacketSendingConfirmation) payload)));
            } else if (payload instanceof SwitchInfoData) {
                emit(input, makeDefaultTuple(input, new SwitchEventCommand((SwitchInfoData) payload)));
            } else if (payload instanceof PortInfoData) {
                emit(input, makeDefaultTuple(input, new PortEventCommand((PortInfoData) payload)));
            } else if (payload instanceof UnmanagedSwitchNotification) {
                emit(input,
                        makeDefaultTuple(input,
                                new SwitchUnmanagedEventCommand((UnmanagedSwitchNotification) payload)));
            } else if (payload instanceof IslBfdFlagUpdated) {
                // FIXME(surabujin): is it ok to consume this "event" from speaker stream?
                emit(STREAM_ISL_ID, input,
                        makeIslTuple(input, new IslBfdFlagUpdatedCommand((IslBfdFlagUpdated) payload)));
            } else {
                log.error("Do not proxy speaker message - unexpected message payload \"{}\"", payload.getClass());
            }
        } catch (PipelineException e) {
            log.error("Error in SpeakerMonitor:", e);
        }
    }


    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
        streamManager.declareStream(STREAM_WATCHER_ID, STREAM_WATCHER_FIELDS);
        streamManager.declareStream(STREAM_ISL_ID, STREAM_ISL_FIELDS);
    }


    private Values makeDefaultTuple(Tuple input, SwitchCommand command) throws PipelineException {
        return new Values(command.getDatapath(), command, pullContext(input));
    }

    private Values makeWatcherTuple(Tuple input, WatcherCommand command) throws PipelineException {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, pullContext(input));
    }

    private Values makeIslTuple(Tuple input, IslCommand command) throws PipelineException {
        IslReference reference = command.getReference();
        return new Values(reference.getSource(), reference.getDest(), command, pullContext(input));
    }
}
