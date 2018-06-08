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

package org.openkilda.floodlight.kafka.producer;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class DefaultWorker extends AbstractWorker {
    public DefaultWorker(Producer<String, String> kafkaProducer, String topic) {
        super(kafkaProducer, topic);
    }

    @Override
    protected SendStatus send(String payload, Callback callback) {
        ProducerRecord<String, String> record = new ProducerRecord<>(getTopic(), payload);
        return new SendStatus(getKafkaProducer().send(record, callback));
    }
}
