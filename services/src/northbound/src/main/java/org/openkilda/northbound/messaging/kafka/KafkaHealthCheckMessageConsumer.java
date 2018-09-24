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

package org.openkilda.northbound.messaging.kafka;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.HealthCheckInfoData;
import org.openkilda.northbound.messaging.HealthCheckMessageConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Kafka Health-Check message producer.
 */
public class KafkaHealthCheckMessageConsumer implements HealthCheckMessageConsumer {
    /**
     * Expected topologies count.
     */
    private static final int HEALTH_CHECK_COMPONENTS_COUNT = 5;

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);

    /**
     * Messages map.
     */
    private volatile Map<String, HealthCheckInfoData> messages = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> poll(final String correlationId) {
        try {
            for (int i = POLL_TIMEOUT / POLL_PAUSE; i < POLL_TIMEOUT; i += POLL_PAUSE) {
                if (HEALTH_CHECK_COMPONENTS_COUNT == messages.size()) {
                    return messages.values().stream().collect(Collectors.toMap(
                            HealthCheckInfoData::getId, HealthCheckInfoData::getState));
                }
                Thread.sleep(POLL_PAUSE);
            }
        } catch (InterruptedException exception) {
            String errorMessage = "Unable to poll message";
            logger.error("{}: {}={}", errorMessage, CORRELATION_ID, correlationId);
            throw new MessageException(correlationId, System.currentTimeMillis(),
                    INTERNAL_ERROR, errorMessage, "kilda-test");
        }
        return messages.values().stream().collect(Collectors.toMap(
                HealthCheckInfoData::getId, HealthCheckInfoData::getState));
    }

    @Override
    public void onResponse(Map<String, String> message) {
        InfoMessage info = (InfoMessage) message;
        HealthCheckInfoData healthCheck = (HealthCheckInfoData) info.getData();
        messages.put(healthCheck.getId(), healthCheck);
    }
}
