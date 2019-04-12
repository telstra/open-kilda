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

package org.openkilda.wfm.topology.utils;

import org.openkilda.messaging.info.InfoData;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.List;

public class InfoDataTranslator extends KafkaRecordTranslator<String, InfoData> {

    @Override
    public List<Object> apply(ConsumerRecord<String, InfoData> record) {
        InfoData data = record.value();
        return new Values(record.key(), data);
    }

    @Override
    public Fields getFieldsFor(String stream) {
        return new Fields(MessageTranslator.KEY_FIELD, MessageTranslator.FIELD_ID_PAYLOAD);
    }
}
