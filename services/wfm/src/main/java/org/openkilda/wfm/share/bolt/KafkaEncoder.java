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

package org.openkilda.wfm.share.bolt;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public abstract class KafkaEncoder extends AbstractBolt {
    public static final String FIELD_ID_PAYLOAD = FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE;
    public static final String FIELD_ID_KEY = FieldNameBasedTupleToKafkaMapper.BOLT_KEY;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD);

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        MessageData payload = pullPayload(input);
        try {
            Message message = wrap(pullContext(input), payload);
            getOutput().emit(input, new Values(pullKey(input), message));
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
            unhandledInput(input);
        }
    }

    protected MessageData pullPayload(Tuple input) throws PipelineException {
        return pullValue(input, FIELD_ID_PAYLOAD, MessageData.class);
    }

    protected String pullKey(Tuple input) {
        String key;
        try {
            key = input.getStringByField(FIELD_ID_KEY);
        } catch (IllegalArgumentException e) {
            key = null;
        }
        return key;
    }

    protected Message wrap(CommandContext commandContext, MessageData payload) {
        Message message = null;
        if (payload instanceof CommandData) {
            message = wrapCommand(commandContext, (CommandData) payload);
        } else if (payload instanceof InfoData) {
            message = wrapInfo(commandContext, (InfoData) payload);
        } else if (payload instanceof ErrorData) {
            message = wrapError(commandContext, (ErrorData) payload);
        } else {
            throw new IllegalArgumentException(String.format("There is not rule to build envelope for: %s", payload));
        }

        return message;
    }

    private CommandMessage wrapCommand(CommandContext commandContext, CommandData payload) {
        return new CommandMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId());
    }

    private InfoMessage wrapInfo(CommandContext commandContext, InfoData payload) {
        return new InfoMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId());
    }

    private ErrorMessage wrapError(CommandContext commandContext, ErrorData payload) {
        return new ErrorMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
