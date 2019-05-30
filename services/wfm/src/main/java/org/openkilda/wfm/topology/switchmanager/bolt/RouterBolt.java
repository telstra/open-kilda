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

package org.openkilda.wfm.topology.switchmanager.bolt;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.switchmanager.command.RemoveKeyRouterBolt;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.Map;

public class RouterBolt extends AbstractBolt {
    public static final String ID = "router.bolt";
    public static final String INCOME_STREAM = "router.bolt.stream";

    private Map<String, String> streams = new HashMap<>();

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        Message message = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof CommandMessage) {
            CommandMessage commandMessage = (CommandMessage) message;
            CommandData data = commandMessage.getData();
            if (data instanceof SwitchValidateRequest) {
                emit(SwitchValidateManager.INCOME_STREAM, input, key, message);
                streams.put(key, SwitchValidateManager.INCOME_STREAM);
            } else if (data instanceof RemoveKeyRouterBolt) {
                streams.remove(((RemoveKeyRouterBolt) data).getKey());
            }
        } else {
            emit(streams.get(key), input, key, message);
        }
    }

    private void emit(String stream, Tuple input, String key, Message message) throws PipelineException {
        CommandContext context = pullContext(input);
        getOutput().emit(stream, input, new Values(key, message, context));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(MessageTranslator.KEY_FIELD, MessageTranslator.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);
        declarer.declareStream(SwitchValidateManager.INCOME_STREAM, fields);
    }
}
