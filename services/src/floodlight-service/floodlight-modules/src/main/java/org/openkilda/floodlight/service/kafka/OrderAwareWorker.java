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

package org.openkilda.floodlight.service.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

class OrderAwareWorker extends AbstractWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderAwareWorker.class);

    private int deep = 1;
    private long expireAt = 0;
    private Integer partition;

    OrderAwareWorker(AbstractWorker worker) {
        super(worker);

        if (worker instanceof OrderAwareWorker) {
            OrderAwareWorker other = (OrderAwareWorker) worker;
            this.deep += other.deep;
            this.partition = other.partition;
        }
    }

    @Override
    protected synchronized SendStatus send(ProducerRecord<String, String> record, Callback callback) {
        ProducerRecord<String, String> actualRecord = record;
        if (partition != null) {
            actualRecord = new ProducerRecord<>(record.topic(), partition, record.key(), record.value());
        }

        Future<RecordMetadata> promise = kafkaProducer.send(actualRecord, callback);
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
            throw new IllegalStateException("Number of .disable() calls have overcome number of .enable() calls");
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
