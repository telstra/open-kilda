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

package org.openkilda.wfm.topology.utils;

import static org.openkilda.wfm.AbstractBolt.FIELD_ID_CONTEXT;

import org.openkilda.messaging.Message;
import org.openkilda.wfm.CommandContext;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.List;

public class MessageTranslator extends KafkaRecordTranslator<String, Message> {
    public static final String KEY_FIELD = "key";
    public static final Fields STREAM_FIELDS = new Fields(KEY_FIELD, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    @Override
    public List<Object> apply(ConsumerRecord<String, Message> record) {
        Message message = record.value();
        CommandContext commandContext = new CommandContext(message);

        return new Values(record.key(), message, commandContext);
    }

    @Override
    public Fields getFieldsFor(String stream) {
        return STREAM_FIELDS;
    }
}
