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
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

public class OrderAwareWorker extends AbstractWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderAwareWorker.class);

    private int deep = 1;
    private long expireAt = 0;
    private Integer partition;

    public OrderAwareWorker(AbstractWorker worker) {
        super(worker.getKafkaProducer(), worker.getTopic());

        if (worker instanceof OrderAwareWorker) {
            OrderAwareWorker other = (OrderAwareWorker) worker;
            this.deep += other.deep;
            this.partition = other.partition;
        }
    }

    public OrderAwareWorker(Producer<String, String> kafkaProducer, String topic) {
        super(kafkaProducer, topic);
    }

    @Override
    protected synchronized SendStatus send(String payload, Callback callback) {
        ProducerRecord<String, String> record;
        if (partition == null) {
            record = new ProducerRecord<>(getTopic(), payload);
        } else {
            record = new ProducerRecord<>(getTopic(), partition, null, payload);
        }

        Future<RecordMetadata> promise = getKafkaProducer().send(record, callback);
        if (partition == null) {
            try {
                partition = promise.get().partition();
            } catch (Exception e) {
                log.error("Can't determine kafka topic partition for order aware writing, due to send error "
                        + "(will retry on next send attempt). Error: {}", e.toString());
            }
        }

        return new SendStatus(promise);
    }

    @Override
    void deactivate(long transitionPeriod) {
        if (deep == 0) {
            throw new IllegalStateException("Number of .diable() calls have overcome number of .enable() calls");
        }

        deep -= 1;
        if (deep == 0) {
            expireAt = System.currentTimeMillis() + transitionPeriod;
        }
    }

    @Override
    boolean isActive() {
        if (0 < deep) {
            return true;
        }
        return System.currentTimeMillis() < expireAt;
    }
}
