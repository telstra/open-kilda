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
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.InstallIslDefaultRulesResult;
import org.openkilda.messaging.info.discovery.RemoveIslDefaultRulesResult;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.SpeakerRulesIslInstalledCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.SpeakerRulesIslRemovedCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.SpeakerRulesWorkerCommand;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class SpeakerRulesRouter extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SPEAKER_RULES_ROUTER.toString();

    public static final String FIELD_ID_KEY = MessageKafkaTranslator.FIELD_ID_KEY;
    public static final String FIELD_ID_INPUT = MessageKafkaTranslator.FIELD_ID_PAYLOAD;

    public static final String STREAM_WORKER_ID = "worker";
    public static final Fields STREAM_WORKER_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_INPUT, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (ComponentId.INPUT_SPEAKER_RULES.toString().equals(source)) {
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
            log.debug("Do not proxy speaker message - unexpected message type \"{}\"", message.getClass());
        }
    }

    private void proxySpeaker(Tuple input, InfoData payload) throws PipelineException {
        if (payload instanceof InstallIslDefaultRulesResult) {
            emit(STREAM_WORKER_ID, input, makeWorkerTuple(new SpeakerRulesIslInstalledCommand(
                    input.getStringByField(FIELD_ID_KEY), (InstallIslDefaultRulesResult) payload)));
        } else if (payload instanceof RemoveIslDefaultRulesResult) {
            emit(STREAM_WORKER_ID, input, makeWorkerTuple(new SpeakerRulesIslRemovedCommand(
                    input.getStringByField(FIELD_ID_KEY), (RemoveIslDefaultRulesResult) payload)));
        } else {
            log.debug("Do not proxy speaker message - unexpected message payload \"{}\"", payload.getClass());
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_WORKER_ID, STREAM_WORKER_FIELDS);

    }

    private Values makeWorkerTuple(SpeakerRulesWorkerCommand command) throws PipelineException {
        return new Values(command.getKey(), command, getCommandContext());
    }

    private Values makeIslTuple(Tuple input, IslCommand command) throws PipelineException {
        IslReference reference = command.getReference();
        return new Values(reference.getSource(), reference.getDest(), command, pullContext(input));
    }
}
