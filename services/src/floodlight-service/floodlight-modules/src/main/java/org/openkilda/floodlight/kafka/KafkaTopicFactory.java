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

package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;

public class KafkaTopicFactory {

    private final ConsumerContext context;

    public KafkaTopicFactory(ConsumerContext context) {
        this.context = context;
    }

    /**
     * Returns specific type for specified response message.
     * @param response response message.
     * @return kafka topic name.
     */
    public String getTopic(FloodlightResponse response) {
        if (response instanceof FlowResponse) {
            return context.getKafkaFlowHsWorkerTopic();
        } else {
            throw new UnsupportedOperationException(String.format("Failed to find kafka topic for %s", response));
        }
    }
}
