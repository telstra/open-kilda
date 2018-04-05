/* Copyright 2017 Telstra Open Source
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


import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.kafka.spout.RecordTranslator;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.List;

public class KafkaRecordTranslator <K, V> implements RecordTranslator<K, V> {
    private static final long serialVersionUID = 1L;

    public static final String FIELD_ID_PAYLOAD = "message";
    public static final String FIELD_ID_TIMESTAMP = "timestamp";
    public static final Fields FIELDS = new Fields(
            FIELD_ID_PAYLOAD, FIELD_ID_TIMESTAMP);

    @Override
    public List<Object> apply(ConsumerRecord<K, V> record) {
        return new Values(record.value(), record.timestamp());
    }

    @Override
    public Fields getFieldsFor(String stream) {
        return FIELDS;
    }

    @Override
    public List<String> streams() {
        return DEFAULT_STREAM;
    }
}
