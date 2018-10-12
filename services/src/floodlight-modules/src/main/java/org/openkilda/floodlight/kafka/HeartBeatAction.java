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

package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.kafka.producer.Producer;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;

import java.util.TimerTask;

public class HeartBeatAction extends TimerTask {
    private final Producer producer;
    private final String topic;

    public HeartBeatAction(Producer producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    @NewCorrelationContextRequired
    public void run() {
        Message message = new org.openkilda.messaging.HeartBeat(System.currentTimeMillis(), CorrelationContext.getId());
        producer.sendMessageAndTrack(topic, message);
    }
}
