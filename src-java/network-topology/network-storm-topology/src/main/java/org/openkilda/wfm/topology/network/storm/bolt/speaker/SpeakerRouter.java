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

package org.openkilda.wfm.topology.network.storm.bolt.speaker;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.info.discovery.InstallIslDefaultRulesResult;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.discovery.RemoveIslDefaultRulesResult;
import org.openkilda.messaging.info.event.DeactivateIslInfoData;
import org.openkilda.messaging.info.event.DeactivateSwitchInfoData;
import org.openkilda.messaging.info.event.FeatureTogglesUpdate;
import org.openkilda.messaging.info.event.IslBfdFlagUpdated;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.messaging.model.system.FeatureTogglesDto;
import org.openkilda.messaging.nbtopology.request.UpdatePortPropertiesRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.mappers.FeatureTogglesMapper;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslBfdFlagUpdatedCommand;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslCommand;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslDefaultRuleCreatedCommand;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslDefaultRuleRemovedCommand;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslDeleteCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.UpdatePortPropertiesCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.bcast.FeatureTogglesNotificationBcast;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.bcast.SpeakerBcast;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.SpeakerBfdSessionResponseCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.SpeakerWorkerCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchEventCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchManagedEventCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchPortEventCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchRemoveEventCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchUnmanagedEventCommand;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.command.WatcherCommand;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.command.WatcherSpeakerDiscoveryCommand;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.command.WatcherSpeakerRoundTripDiscovery;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.command.WatcherSpeakerSendConfirmationCommand;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class SpeakerRouter extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SPEAKER_ROUTER.toString();

    public static final String FIELD_ID_KEY = MessageKafkaTranslator.KEY_FIELD;
    public static final String FIELD_ID_INPUT = MessageKafkaTranslator.FIELD_ID_PAYLOAD;
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

    public static final String STREAM_BCAST_ID = "notification";
    public static final Fields STREAM_BCAST_FIELDS = new Fields(FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_PORT_ID = "port";
    public static final Fields STREAM_PORT_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_WORKER_ID = "worker";
    public static final Fields STREAM_WORKER_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_INPUT, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (ComponentId.INPUT_SPEAKER.toString().equals(source)) {
            Message message = pullValue(input, FIELD_ID_INPUT, Message.class);
            speakerMessage(input, message);
        } else {
            unhandledInput(input);
        }
    }

    private void speakerMessage(Tuple input, Message message) throws PipelineException {
        proxySpeaker(input, message);
    }

    private void proxySpeaker(Tuple input, Message message) throws PipelineException {
        if (message instanceof InfoMessage) {
            proxySpeaker(input, ((InfoMessage) message).getData());
        } else {
            log.error("Do not proxy speaker message - unexpected message type \"{}\"", message.getClass());
        }
    }

    private void proxySpeaker(Tuple input, InfoData payload) throws PipelineException {
        if (payload instanceof IslInfoData) {
            emit(STREAM_WATCHER_ID, input, makeWatcherTuple(
                    input, new WatcherSpeakerDiscoveryCommand((IslInfoData) payload)));
        } else if (payload instanceof DiscoPacketSendingConfirmation) {
            emit(STREAM_WATCHER_ID, input, makeWatcherTuple(
                    input, new WatcherSpeakerSendConfirmationCommand((DiscoPacketSendingConfirmation) payload)));
        } else if (payload instanceof IslRoundTripLatency) {
            emit(STREAM_WATCHER_ID, input, makeWatcherTuple(
                    input, new WatcherSpeakerRoundTripDiscovery((IslRoundTripLatency) payload)));
        } else if (payload instanceof SwitchInfoData) {
            emit(input, makeDefaultTuple(input, new SwitchEventCommand((SwitchInfoData) payload)));
        } else if (payload instanceof PortInfoData) {
            emit(input, makeDefaultTuple(input, new SwitchPortEventCommand((PortInfoData) payload)));
        } else if (payload instanceof NetworkDumpSwitchData) {
            emit(input, makeDefaultTuple(
                    input, new SwitchManagedEventCommand(((NetworkDumpSwitchData) payload).getSwitchView())));
        } else if (payload instanceof UnmanagedSwitchNotification) {
            emit(input, makeDefaultTuple(
                    input, new SwitchUnmanagedEventCommand(((UnmanagedSwitchNotification) payload).getSwitchId())));
        } else if (payload instanceof DeactivateSwitchInfoData) {
            emit(input, makeDefaultTuple(
                    input, new SwitchRemoveEventCommand((DeactivateSwitchInfoData) payload)));
        } else if (payload instanceof BfdSessionResponse) {
            emit(STREAM_WORKER_ID, input, makeWorkerTuple(new SpeakerBfdSessionResponseCommand(
                    input.getStringByField(FIELD_ID_KEY), (BfdSessionResponse) payload)));
        } else if (payload instanceof InstallIslDefaultRulesResult) {
            emit(STREAM_ISL_ID, input, makeIslTuple(input, new IslDefaultRuleCreatedCommand(
                    (InstallIslDefaultRulesResult) payload)));
        } else if (payload instanceof RemoveIslDefaultRulesResult) {
            emit(STREAM_ISL_ID, input, makeIslTuple(input, new IslDefaultRuleRemovedCommand(
                    (RemoveIslDefaultRulesResult) payload)));
        } else if (payload instanceof IslBfdFlagUpdated) {
            // FIXME(surabujin): is it ok to consume this "event" from speaker stream?
            emit(STREAM_ISL_ID, input, makeIslTuple(input, new IslBfdFlagUpdatedCommand((IslBfdFlagUpdated) payload)));
        } else if (payload instanceof FeatureTogglesUpdate) {
            FeatureTogglesDto toggles = ((FeatureTogglesUpdate) payload).getToggles();
            emit(STREAM_BCAST_ID, input, makeBcastTuple(new FeatureTogglesNotificationBcast(
                    FeatureTogglesMapper.INSTANCE.map(toggles))));
        } else if (payload instanceof UpdatePortPropertiesRequest) {
            emit(STREAM_PORT_ID, input, makePortTuple(
                    new UpdatePortPropertiesCommand((UpdatePortPropertiesRequest) payload)));
        } else if (payload instanceof DeactivateIslInfoData) {
            emit(STREAM_ISL_ID, input, makeIslTuple(input, new IslDeleteCommand((DeactivateIslInfoData) payload)));
        } else {
            log.error("Do not proxy speaker message - unexpected message payload \"{}\"", payload.getClass());
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
        streamManager.declareStream(STREAM_WATCHER_ID, STREAM_WATCHER_FIELDS);
        streamManager.declareStream(STREAM_ISL_ID, STREAM_ISL_FIELDS);
        streamManager.declareStream(STREAM_WORKER_ID, STREAM_WORKER_FIELDS);
        streamManager.declareStream(STREAM_BCAST_ID, STREAM_BCAST_FIELDS);
        streamManager.declareStream(STREAM_PORT_ID, STREAM_PORT_FIELDS);
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

    private Values makeWorkerTuple(SpeakerWorkerCommand command) throws PipelineException {
        return new Values(command.getKey(), command, getCommandContext());
    }

    private Values makeBcastTuple(SpeakerBcast command) {
        return new Values(command, getCommandContext());
    }

    private Values makePortTuple(PortCommand command) {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, getCommandContext());
    }
}
