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

import static java.util.Objects.requireNonNull;

import org.openkilda.floodlight.kafka.producer.Producer;

import java.util.Timer;
import java.util.TimerTask;

public class HeartBeat {
    private final Producer producer;
    private final long interval;
    private final String topoDiscoTopic;

    private final Timer timer;
    private TimerTask task;

    public HeartBeat(Producer producer, long interval, String topoDiscoTopic) {
        this.producer = producer;
        this.interval = interval;
        this.topoDiscoTopic = requireNonNull(topoDiscoTopic, "topoDiscoTopic cannot be null");

        task = new HeartBeatAction(producer, topoDiscoTopic);
        timer = new Timer("kafka.HeartBeat", true);
        timer.scheduleAtFixedRate(task, interval, interval);
    }

    /**
     * Postpone execution - restart wait cycle from zero.
     */
    public void reschedule() {
        TimerTask replace = new HeartBeatAction(producer, topoDiscoTopic);
        timer.scheduleAtFixedRate(replace, interval, interval);

        synchronized (this) {
            task.cancel();
            task = replace;
        }
    }
}
